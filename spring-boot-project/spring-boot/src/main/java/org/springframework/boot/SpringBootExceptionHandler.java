/*
 * Copyright 2012-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot;

import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * {@link UncaughtExceptionHandler} to suppress handling already logged exceptions and
 * dealing with system exit.
 *
 * @author Phillip Webb
 */
class SpringBootExceptionHandler implements UncaughtExceptionHandler {

	private static final Set<String> LOG_CONFIGURATION_MESSAGES;

	/**
	 * 静态代码块:
	 * 属于类实例，随着类的加载而执行，而且只执行一次，先于实例构造函数执行
	 *
	 * 非静态代码块:
	 * 执行的时候如果有静态初始化块，先执行静态初始化块再执行非静态初始化块，
	 * 在每个对象生成时都会被执行一次，它可以初始化类的实例变量。非静态初始化块会在构造函数执行时，
	 * 在构造函数主体代码执行之前被运行。例如下面的非静态代码块
	 * {
	 * 	 System.out.println("helloworld");
	 * }
	 *
	 */
	static {
		Set<String> messages = new HashSet<>();
		messages.add("Logback configuration error detected");
		LOG_CONFIGURATION_MESSAGES = Collections.unmodifiableSet(messages);
	}

	private static final LoggedExceptionHandlerThreadLocal handler = new LoggedExceptionHandlerThreadLocal();

	private final UncaughtExceptionHandler parent;

	private final List<Throwable> loggedExceptions = new ArrayList<>();

	private int exitCode = 0;

	SpringBootExceptionHandler(UncaughtExceptionHandler parent) {
		this.parent = parent;
	}

	void registerLoggedException(Throwable exception) {
		this.loggedExceptions.add(exception);
	}

	void registerExitCode(int exitCode) {
		this.exitCode = exitCode;
	}

	@Override
	public void uncaughtException(Thread thread, Throwable ex) {
		try {
			if (isPassedToParent(ex) && this.parent != null) {
				this.parent.uncaughtException(thread, ex);
			}
		}
		finally {
			this.loggedExceptions.clear();
			if (this.exitCode != 0) {
				System.exit(this.exitCode);
			}
		}
	}

	private boolean isPassedToParent(Throwable ex) {
		return isLogConfigurationMessage(ex) || !isRegistered(ex);
	}

	/**
	 * Check if the exception is a log configuration message, i.e. the log call might not
	 * have actually output anything.
	 * @param ex the source exception
	 * @return {@code true} if the exception contains a log configuration message
	 */
	private boolean isLogConfigurationMessage(Throwable ex) {
		if (ex instanceof InvocationTargetException) {
			/**
			 * 这里跟上面的方法调用也是递归调用，递归退出的时机是ex.getCause不再属于
			 * InvocationTargetException
			 */
			return isLogConfigurationMessage(ex.getCause());
		}
		/**
		 * 过滤消息判断ex的消息是不是配置的常量LOG_CONFIGURATION_MESSAGES
		 */
		String message = ex.getMessage();
		if (message != null) {
			for (String candidate : LOG_CONFIGURATION_MESSAGES) {
				if (message.contains(candidate)) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean isRegistered(Throwable ex) {
		if (this.loggedExceptions.contains(ex)) {
			return true;
		}
		if (ex instanceof InvocationTargetException) {
			/**
			 * 跟上面的方法组合一起进行递归调用，推出机制是上面的返回true或者不再属于
			 * InvocationTargetException就返回false进行退出
			 */
			return isRegistered(ex.getCause());
		}
		return false;
	}

	static SpringBootExceptionHandler forCurrentThread() {
		return handler.get();
	}

	/**
	 * Thread local used to attach and track handlers.
	 */
	private static final class LoggedExceptionHandlerThreadLocal extends ThreadLocal<SpringBootExceptionHandler> {

		/**
		 * 关于initValue方式的注释见下面:
		 *
		 * Returns the current thread's "initial value" for this thread-local variable.
		 * This method will be invoked the first time a thread accesses the variable with
		 * the get method, unless the thread previously invoked the set method, in which
		 * case the initialValue method will not be invoked for the thread. Normally,
		 * this method is invoked at most once per thread, but it may be invoked again in
		 * case of subsequent invocations of remove followed by get.
		 *
		 * This implementation simply returns null; if the programmer desires thread-local
		 * variables to have an initial value other than null, ThreadLocal must be
		 * subclassed, and this method overridden. Typically, an anonymous inner class
		 * will be used.
		 */
		@Override
		protected SpringBootExceptionHandler initialValue() {
			/**
			 * 下面的代码有点意思，先取出当前线程的UncaughtExceptionHandler，根据取出的handler
			 * 构建子类SpringBootExceptionHandler，再把子类handler赋值给当前线程的handler
			 */
			SpringBootExceptionHandler handler = new SpringBootExceptionHandler(
					Thread.currentThread().getUncaughtExceptionHandler());
			Thread.currentThread().setUncaughtExceptionHandler(handler);
			return handler;
		}

	}

	/**
	 * suppress 抑制
	 * handler 句柄
	 */

}
