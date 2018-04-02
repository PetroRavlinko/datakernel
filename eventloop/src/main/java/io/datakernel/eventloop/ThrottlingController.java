/*
 * Copyright (C) 2015 SoftIndex LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.datakernel.eventloop;

import io.datakernel.jmx.EventloopJmxMBean;
import io.datakernel.jmx.JmxAttribute;
import io.datakernel.jmx.JmxOperation;
import io.datakernel.jmx.JmxReducers.JmxReducerSum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Random;

import static io.datakernel.util.Preconditions.checkArgument;
import static java.lang.Math.pow;

public final class ThrottlingController implements EventloopJmxMBean {
	private static int staticInstanceCounter = 0;

	private final Logger logger = LoggerFactory.getLogger(ThrottlingController.class.getName() + "." + staticInstanceCounter++);

	public static final Duration TARGET_TIME = Duration.ofMillis(20);
	public static final Duration GC_TIME = Duration.ofMillis(20);
	public static final int SMOOTHING_WINDOW = 10000;
	public static final double THROTTLING_DECREASE = 0.1;
	public static final double INITIAL_KEYS_PER_SECOND = 100;
	public static final double INITIAL_THROTTLING = 0.0;

	private static final Random random = new Random() {
		private long prev = System.nanoTime();

		@Override
		protected int next(int nbits) {
			long x = this.prev;
			x ^= (x << 21);
			x ^= (x >>> 35);
			x ^= (x << 4);
			this.prev = x;
			x &= ((1L << nbits) - 1);
			return (int) x;
		}
	};

	private Eventloop eventloop;

	// region settings
	private int targetTimeMillis;
	private int gcTimeMillis;
	private double throttlingDecrease;
	private int smoothingWindow;
	// endregion

	// region intermediate counters for current round
	private int bufferedRequests;
	private int bufferedRequestsThrottled;
	// endregion

	// region exponentially smoothed values
	private double smoothedThrottling;
	private double smoothedTimePerKeyMillis;
	// endregion

	// region JMX
	private long infoTotalRequests;
	private long infoTotalRequestsThrottled;
	private long infoTotalTimeMillis;
	private long infoRounds;
	private long infoRoundsZeroThrottling;
	private long infoRoundsExceededTargetTime;
	private long infoRoundsGc;
	// endregion

	private float throttling;

	// region creators
	private ThrottlingController() {
	}

	public static ThrottlingController create() {
		return new ThrottlingController()
				.withTargetTime(TARGET_TIME)
				.withGcTime(GC_TIME)
				.withSmoothingWindow(SMOOTHING_WINDOW)
				.withThrottlingDecrease(THROTTLING_DECREASE)
				.withInitialKeysPerSecond(INITIAL_KEYS_PER_SECOND)
				.withInitialThrottling(INITIAL_THROTTLING);
	}

	public ThrottlingController withTargetTime(long targetTimeMillis) {
		setTargetTimeMillis((int) targetTimeMillis);
		return this;
	}

	public ThrottlingController withTargetTime(Duration targetTime) {
		return withTargetTime(targetTime.toMillis());
	}

	public ThrottlingController withGcTime(Duration gcTime) {
		return withGcTime(gcTime.toMillis());
	}

	public ThrottlingController withGcTime(long gcTimeMillis) {
		setGcTimeMillis((int) gcTimeMillis);
		return this;
	}

	public ThrottlingController withSmoothingWindow(int smoothingWindow) {
		setSmoothingWindow(smoothingWindow);
		return this;
	}

	public ThrottlingController withThrottlingDecrease(double throttlingDecrease) {
		setThrottlingDecrease(throttlingDecrease);
		return this;
	}

	public ThrottlingController withInitialKeysPerSecond(double initialKeysPerSecond) {
		checkArgument(initialKeysPerSecond > 0, "Initial keys per second should not be zero or less");
		this.smoothedTimePerKeyMillis = 1000.0 / initialKeysPerSecond;
		return this;
	}

	public ThrottlingController withInitialThrottling(double initialThrottling) {
		checkArgument(initialThrottling >= 0, "Initial throttling should not be zero or less");
		this.smoothedThrottling = initialThrottling;
		return this;
	}

	// endregion

	public boolean isOverloaded() {
		bufferedRequests++;
		if (random.nextFloat() < throttling) {
			bufferedRequestsThrottled++;
			return true;
		}
		return false;
	}

	void updateInternalStats(int lastKeys, int lastTime) {
		if (lastTime < 0 || lastTime > 60000) {
			logger.warn("Invalid processing time: {}", lastTime);
			return;
		}

		int lastTimePredicted = (int) (lastKeys * getAvgTimePerKeyMillis());
		if (gcTimeMillis != 0.0 && lastTime > lastTimePredicted + gcTimeMillis) {
			logger.debug("GC detected {} ms, {} keys", lastTime, lastKeys);
			lastTime = lastTimePredicted + gcTimeMillis;
			infoRoundsGc++;
		}

		double weight = 1.0 - 1.0 / smoothingWindow;

		if (bufferedRequests != 0) {
			assert bufferedRequestsThrottled <= bufferedRequests;
			double value = (double) bufferedRequestsThrottled / bufferedRequests;
			smoothedThrottling = (smoothedThrottling - value) * pow(weight, bufferedRequests) + value;
			infoTotalRequests += bufferedRequests;
			infoTotalRequestsThrottled += bufferedRequestsThrottled;
			bufferedRequests = 0;
			bufferedRequestsThrottled = 0;
		}

		if (lastKeys != 0) {
			double value = (double) lastTime / lastKeys;
			smoothedTimePerKeyMillis = (smoothedTimePerKeyMillis - value) * pow(weight, lastKeys) + value;
		}

		infoTotalTimeMillis += lastTime;
	}

	void calculateThrottling(int newKeys) {
		double predictedTime = newKeys * getAvgTimePerKeyMillis();

		double newThrottling = getAvgThrottling() - throttlingDecrease;
		if (newThrottling < 0)
			newThrottling = 0;
		if (predictedTime > targetTimeMillis) {
			double extraThrottling = 1.0 - targetTimeMillis / predictedTime;
			if (extraThrottling > newThrottling) {
				newThrottling = extraThrottling;
				infoRoundsExceededTargetTime++;
			}
		}

		if (newThrottling == 0)
			infoRoundsZeroThrottling++;
		infoRounds++;

		this.throttling = (float) newThrottling;
	}

	public double getAvgTimePerKeyMillis() {
		return smoothedTimePerKeyMillis;
	}

	@JmxAttribute
	public double getAvgKeysPerSecond() {
		return 1000.0 / getAvgTimePerKeyMillis();
	}

	@JmxAttribute
	public double getAvgThrottling() {
		return smoothedThrottling;
	}

	@JmxAttribute
	public int getTargetTimeMillis() {
		return targetTimeMillis;
	}

	@JmxAttribute
	public void setTargetTimeMillis(int targetTimeMillis) {
		checkArgument(targetTimeMillis > 0, "Target time should not be zero or less");
		this.targetTimeMillis = targetTimeMillis;
	}


	@JmxAttribute
	public int getGcTimeMillis() {
		return gcTimeMillis;
	}

	@JmxAttribute
	public void setGcTimeMillis(int gcTimeMillis) {
		checkArgument(gcTimeMillis > 0, "GC time should not be zero or less");
		this.gcTimeMillis = gcTimeMillis;
	}

	@JmxAttribute
	public double getThrottlingDecrease() {
		return throttlingDecrease;
	}

	@JmxAttribute
	public void setThrottlingDecrease(double throttlingDecrease) {
		checkArgument(throttlingDecrease >= 0.0 && throttlingDecrease <= 1.0, "Throttling decrease should not fall out of [0;1] range");
		this.throttlingDecrease = throttlingDecrease;
	}

	@JmxAttribute
	public int getSmoothingWindow() {
		return smoothingWindow;
	}

	@JmxAttribute
	public void setSmoothingWindow(int smoothingWindow) {
		checkArgument(smoothingWindow > 0, "Smoothing window should not be zero or less");
		this.smoothingWindow = smoothingWindow;
	}

	@JmxAttribute(reducer = JmxReducerSum.class)
	public long getTotalRequests() {
		return infoTotalRequests;
	}

	@JmxAttribute(reducer = JmxReducerSum.class)
	public long getTotalRequestsThrottled() {
		return infoTotalRequestsThrottled;
	}

	@JmxAttribute(reducer = JmxReducerSum.class)
	public long getTotalProcessed() {
		return infoTotalRequests - infoTotalRequestsThrottled;
	}

	@JmxAttribute(reducer = JmxReducerSum.class)
	public long getTotalTimeMillis() {
		return infoTotalTimeMillis;
	}

	@JmxAttribute(reducer = JmxReducerSum.class)
	public long getRounds() {
		return infoRounds;
	}

	@JmxAttribute(reducer = JmxReducerSum.class)
	public long getRoundsZeroThrottling() {
		return infoRoundsZeroThrottling;
	}

	@JmxAttribute(reducer = JmxReducerSum.class)
	public long getRoundsExceededTargetTime() {
		return infoRoundsExceededTargetTime;
	}

	@JmxAttribute(reducer = JmxReducerSum.class)
	public long getInfoRoundsGc() {
		return infoRoundsGc;
	}

	@JmxAttribute
	public double getThrottling() {
		return throttling;
	}

	@JmxOperation
	public void resetInfo() {
		infoTotalRequests = 0;
		infoTotalRequestsThrottled = 0;
		infoTotalTimeMillis = 0;
		infoRounds = 0;
		infoRoundsZeroThrottling = 0;
		infoRoundsExceededTargetTime = 0;
	}

	@Override
	public String toString() {
		return String.format("{throttling:%2d%% avgKps=%-4d avgThrottling=%2d%% requests=%-4d throttled=%-4d rounds=%-3d zero=%-3d >targetTime=%-3d}",
				(int) (throttling * 100),
				(int) getAvgKeysPerSecond(),
				(int) (getAvgThrottling() * 100),
				infoTotalRequests,
				infoTotalRequestsThrottled,
				infoRounds,
				infoRoundsZeroThrottling,
				infoRoundsExceededTargetTime);
	}

	void setEventloop(Eventloop eventloop) {
		this.eventloop = eventloop;
	}

	@Override
	public Eventloop getEventloop() {
		return eventloop;
	}
}
