/*
 * Copyright (C) 2015-2018 SoftIndex LLC.
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

package io.datakernel.multilog;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class LogPosition implements Comparable<LogPosition> {
	@NotNull
	private final LogFile logFile;
	private final long position;

	private LogPosition(@NotNull LogFile logFile, long position) {
		this.logFile = logFile;
		this.position = position;
	}

	public static LogPosition create(@NotNull LogFile logFile, long position) {
		return new LogPosition(logFile, position);
	}

	public boolean isBeginning() {
		return position == 0L;
	}

	@NotNull
	public LogFile getLogFile() {
		return logFile;
	}

	public long getPosition() {
		return position;
	}

	@SuppressWarnings("RedundantIfStatement")
	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		LogPosition that = (LogPosition) o;
		if (position != that.position) return false;
		if (!Objects.equals(logFile, that.logFile)) return false;
		return true;
	}

	@Override
	public int hashCode() {
		int result = logFile.hashCode();
		result = 31 * result + (int) (position ^ (position >>> 32));
		return result;
	}

	@Override
	public String toString() {
		return "LogPosition{logFile=" + logFile + ", position=" + position + '}';
	}

	@Override
	public int compareTo(@NotNull LogPosition o) {
		int result = 0;
		result = this.logFile.compareTo(o.logFile);
		if (result != 0)
			return result;
		return Long.compare(this.position, o.position);
	}
}
