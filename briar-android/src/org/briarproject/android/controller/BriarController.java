package org.briarproject.android.controller;

import org.briarproject.android.controller.handler.ResultHandler;

public interface BriarController extends ActivityLifecycleController {

	void startAndBindService();

	boolean hasEncryptionKey();

	void signOut(ResultHandler<Void> eventHandler);
}