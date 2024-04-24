/*
 * Copyright 2012-2023 the original author or authors.
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

import java.util.concurrent.Callable;

import org.apache.commons.logging.Log;

import org.springframework.aot.AotDetector;
import org.springframework.boot.SpringApplication.Startup;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.boot.system.ApplicationPid;
import org.springframework.context.ApplicationContext;
import org.springframework.core.log.LogMessage;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * Logs application information on startup.
 *
 * @author Phillip Webb
 * @author Dave Syer
 * @author Moritz Halbritter
 */
class StartupInfoLogger {

	/**
	 * 关于变量sourceClass使用了final修饰，使用final修饰的变量不是不能被修改吗？
	 * 关键在于初始化，在变量定义的时候其实没有初始化，一旦初始化后就不能修改赋值了，不然编译器会报错
	 *
	 * Class<?> 属于泛型编程中的通配符(Wildcard),通常有指定继承父类的子类的通配符，例如：
	 * public static void printBuddies(Pair<? extends Employee> p) {
	 *     ......
	 * }
	 * 这里Pair的泛型参数就是满足继承了Employee的类。
	 * 回到Class<?>,就是通配符参数，表达的意思就是任意的类
	 *
	 */
	private final Class<?> sourceClass;

	StartupInfoLogger(Class<?> sourceClass) {
		this.sourceClass = sourceClass;
	}

	void logStarting(Log applicationLog) {
		Assert.notNull(applicationLog, "Log must not be null");
		applicationLog.info(LogMessage.of(this::getStartingMessage));
		applicationLog.debug(LogMessage.of(this::getRunningMessage));
	}

	void logStarted(Log applicationLog, Startup startup) {
		if (applicationLog.isInfoEnabled()) {
			applicationLog.info(getStartedMessage(startup));
		}
	}

	private CharSequence getStartingMessage() {
		/**
		 * 下面的StringBuilder添加log的方式实现了设计模式中的装饰模式
		 * 每过一道，增加一次修饰，把每一道工序抽离出来进行单独逻辑处理
		 * 这是一种很好的编程方式，而不是把全部逻辑放在这个方法里实现
		 */
		StringBuilder message = new StringBuilder();
		message.append("Starting");
		appendAotMode(message);
		appendApplicationName(message);
		appendVersion(message, this.sourceClass);
		appendJavaVersion(message);
		appendPid(message);
		appendContext(message);
		return message;
	}

	private CharSequence getRunningMessage() {
		StringBuilder message = new StringBuilder();
		message.append("Running with Spring Boot");
		appendVersion(message, getClass());
		message.append(", Spring");
		appendVersion(message, ApplicationContext.class);
		return message;
	}

	private CharSequence getStartedMessage(Startup startup) {
		StringBuilder message = new StringBuilder();
		message.append(startup.action());
		appendApplicationName(message);
		message.append(" in ");
		message.append(startup.timeTakenToStarted().toMillis() / 1000.0);
		message.append(" seconds");
		Long uptimeMs = startup.processUptime();
		if (uptimeMs != null) {
			double uptime = uptimeMs / 1000.0;
			message.append(" (process running for ").append(uptime).append(")");
		}
		return message;
	}

	private void appendAotMode(StringBuilder message) {
		append(message, "", () -> AotDetector.useGeneratedArtifacts() ? "AOT-processed" : null);
	}

	private void appendApplicationName(StringBuilder message) {
		append(message, "",
				() -> (this.sourceClass != null) ? ClassUtils.getShortName(this.sourceClass) : "application");
	}

	private void appendVersion(StringBuilder message, Class<?> source) {
		append(message, "v", () -> source.getPackage().getImplementationVersion());
	}

	private void appendPid(StringBuilder message) {
		append(message, "with PID ", ApplicationPid::new);
	}

	private void appendContext(StringBuilder message) {
		StringBuilder context = new StringBuilder();
		ApplicationHome home = new ApplicationHome(this.sourceClass);
		if (home.getSource() != null) {
			context.append(home.getSource().getAbsolutePath());
		}
		append(context, "started by ", () -> System.getProperty("user.name"));
		append(context, "in ", () -> System.getProperty("user.dir"));
		if (!context.isEmpty()) {
			message.append(" (");
			message.append(context);
			message.append(")");
		}
	}

	private void appendJavaVersion(StringBuilder message) {
		append(message, "using Java ", () -> System.getProperty("java.version"));
	}

	private void append(StringBuilder message, String prefix, Callable<Object> call) {
		append(message, prefix, call, "");
	}

	private void append(StringBuilder message, String prefix, Callable<Object> call, String defaultValue) {
		Object result = callIfPossible(call);
		String value = (result != null) ? result.toString() : null;
		if (!StringUtils.hasLength(value)) {
			value = defaultValue;
		}
		if (StringUtils.hasLength(value)) {
			message.append((!message.isEmpty()) ? " " : "");
			message.append(prefix);
			message.append(value);
		}
	}

	private Object callIfPossible(Callable<Object> call) {
		try {
			return call.call();
		}
		catch (Exception ex) {
			return null;
		}
	}

	/**
	 * 关于功能性接口(@FunctionalInterface修饰的接口),就定义了一个方法，可以使用lambda进行实现。
	 * 像上面的调用过程中
	 * private void append(StringBuilder message, String prefix, Callable<Object> call,
	 * String defaultValue) {
	 *
	 * }
	 * 这个append方法使用了Callable<Object> call这个参数，这就是一个功能性接口，而在调用的地方使用
	 * append(message, "", () -> AotDetector.useGeneratedArtifacts() ? "AOT-processed" : null);
	 * 其中的Callable<Object> call参数的入参就是一个是实现了Callable接口的lambda(可以理解为类)
	 * 上面的() -> AotDetector.useGeneratedArtifacts() ? "AOT-processed" : null是lambda表达式，
	 * 可以使用下面的代码代替：
	 * () -> {return (this.sourceClass != null) ? ClassUtils.getShortName(this.sourceClass) : "application";}
	 *
	 * 下面是关于Callable功能性接口的描述
	 * A task that returns a result and may throw an exception. Implementors define a single
	 * method with no arguments called call. The Callable interface is similar to Runnable,
	 * in that both are designed for classes whose instances are potentially executed by
	 * another thread. A Runnable, however, does not return a result and cannot throw a checked exception.
	 *
	 */

}
