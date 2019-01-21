package io.datakernel.async;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public interface AsyncProcess extends Cancellable {
	@Contract(pure = true)
	@NotNull
	Promise<Void> getProcessResult();

	@NotNull
	Promise<Void> startProcess();
}
