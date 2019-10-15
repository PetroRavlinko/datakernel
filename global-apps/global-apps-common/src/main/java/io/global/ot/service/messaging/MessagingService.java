package io.global.ot.service.messaging;

import io.datakernel.async.AsyncSupplier;
import io.datakernel.async.Promise;
import io.datakernel.async.Promises;
import io.datakernel.async.SettablePromise;
import io.datakernel.eventloop.Eventloop;
import io.datakernel.eventloop.EventloopService;
import io.datakernel.exception.StacklessException;
import io.datakernel.ot.OTStateManager;
import io.datakernel.util.ApplicationSettings;
import io.global.common.KeyPair;
import io.global.common.PubKey;
import io.global.ot.api.CommitId;
import io.global.ot.service.CommonUserContainer;
import io.global.ot.shared.CreateOrDropRepos;
import io.global.ot.shared.SharedReposOTState;
import io.global.ot.shared.SharedReposOperation;
import io.global.pm.Message;
import io.global.pm.Messenger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Set;

import static io.datakernel.async.AsyncSuppliers.retry;
import static io.datakernel.async.Promises.repeat;
import static io.global.ot.OTUtils.POLL_RETRY_POLICY;
import static io.global.util.Utils.eitherComplete;

public final class MessagingService implements EventloopService {
	public static final StacklessException STOPPED_EXCEPTION = new StacklessException(MessagingService.class, "Service has been stopped");
	@NotNull
	public static final Duration POLL_INTERVAL = ApplicationSettings.getDuration(MessagingService.class, "message.poll.interval", Duration.ofSeconds(5));

	private final Eventloop eventloop;
	private final Messenger<Long, CreateSharedRepo> messenger;
	private final CommonUserContainer<?> commonUserContainer;
	private final String mailBox;

	private SettablePromise<Message<Long, CreateSharedRepo>> stopPromise = new SettablePromise<>();

	private MessagingService(Eventloop eventloop, Messenger<Long, CreateSharedRepo> messenger, CommonUserContainer<?> commonUserContainer, String mailBox) {
		this.eventloop = eventloop;
		this.messenger = messenger;
		this.commonUserContainer = commonUserContainer;
		this.mailBox = mailBox;
	}

	public static MessagingService create(Eventloop eventloop, Messenger<Long, CreateSharedRepo> messenger,
			CommonUserContainer<?> commonUserContainer, String mailBox) {
		return new MessagingService(eventloop, messenger, commonUserContainer, mailBox);
	}

	@NotNull
	@Override
	public Eventloop getEventloop() {
		return eventloop;
	}

	@NotNull
	@Override
	public Promise<Void> start() {
		pollMessages();
		return Promise.complete();
	}

	@NotNull
	@Override
	public Promise<Void> stop() {
		stopPromise.trySetException(STOPPED_EXCEPTION);
		return Promise.complete();
	}

	public Promise<Void> sendCreateMessage(PubKey receiver, String id, String name, Set<PubKey> participants) {
		KeyPair keys = commonUserContainer.getKeys();
		CreateSharedRepo payload = new CreateSharedRepo(id, name, participants);
		return messenger.send(keys, receiver, mailBox, payload).toVoid();
	}

	@SuppressWarnings("ConstantConditions")
	private void pollMessages() {
		KeyPair keys = commonUserContainer.getMyRepositoryId().getPrivKey().computeKeys();
		OTStateManager<CommitId, SharedReposOperation> stateManager = commonUserContainer.getStateManager();
		SharedReposOTState state = (SharedReposOTState) stateManager.getState();
		AsyncSupplier<@Nullable Message<Long, CreateSharedRepo>> messagesSupplier = retry(() -> messenger.poll(keys, mailBox),
				POLL_RETRY_POLICY);

		repeat(() -> eitherComplete(messagesSupplier.get(), stopPromise)
				.then(message -> {
					if (message == null) {
						return Promises.delay(POLL_INTERVAL, Promise.complete());
					}
					CreateSharedRepo createSharedRepo = message.getPayload();
					return Promise.complete()
							.then($ -> {
								String id = createSharedRepo.getId();
								if (state.getSharedRepos().containsKey(id)) {
									return Promise.complete();
								}
								String name = createSharedRepo.getName();
								Set<PubKey> participants = createSharedRepo.getParticipants();
								SharedReposOperation createOp = CreateOrDropRepos.create(id, name, participants);
								stateManager.add(createOp);
								return stateManager.sync()
										.whenException(e -> stateManager.reset());
							})
							.then($ -> messenger.drop(keys, mailBox, message.getId()))
							.toTry()
							.toVoid();
				}));
	}

}
