/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.util.StringValueResolver;

/**
 * 翻译：别名注册接口的简单实现
 * @date 2019年11月23日12:23:31
 *
 * Simple implementation of the {@link AliasRegistry} interface.
 * Serves as base class for
 * {@link org.springframework.beans.factory.support.BeanDefinitionRegistry}
 * implementations.
 *
 * @author Juergen Hoeller
 * @since 2.5.2
 */
public class SimpleAliasRegistry implements AliasRegistry {

	/** Logger available to subclasses */
	protected final Log logger = LogFactory.getLog(getClass());

	/** Map from alias to canonical name */
	private final Map<String, String> aliasMap = new ConcurrentHashMap<>(16);


	@Override
	public void registerAlias(String name, String alias) {
		// 原名判空
		Assert.hasText(name, "'name' must not be empty");
		// 别名判空
		Assert.hasText(alias, "'alias' must not be empty");
		// 注册别名过程使用串行
		synchronized (this.aliasMap) {
			// 对比别名是否与原类名相同
			if (alias.equals(name)) {
				// 如果相同则删除 map 里的别名，这里相当于其他 bean 不能使用这个别名，因为和传入的原名重复了，原名优先
				this.aliasMap.remove(alias);
				if (logger.isDebugEnabled()) {
					// 翻译：别名定义 alias 由于指向同一名称而被忽略
					logger.debug("Alias definition '" + alias + "' ignored since it points to same name");
				}
			}
			else {
				//如果别名与原名不相同，先尝试取出 map 中的别名
				String registeredName = this.aliasMap.get(alias);
				// 如果 map 中含有此别名，则说明这个别名被某个 bean 占用了
				if (registeredName != null) {
					// 如果 map 中原名与传入的原名相同，证明是同一个 bean，不需要重复注册
					if (registeredName.equals(name)) {
						// An existing alias - no need to re-register
						return;
					}
					// 查看是否允许别名覆盖（默认允许），如果不允许则抛异常
					if (!allowAliasOverriding()) {
						throw new IllegalStateException("Cannot define alias '" + alias + "' for name '" +
								name + "': It is already registered for name '" + registeredName + "'.");
					}
					// 输出别名覆盖的日志，原 bean 的别名失效，新 bean 的别名生效
					if (logger.isInfoEnabled()) {
						logger.info("Overriding alias '" + alias + "' definition for registered name '" +
								registeredName + "' with new target name '" + name + "'");
					}
				}
				// 检查是否有别名循环，如果有抛出异常
				checkForAliasCircle(name, alias);
				// 加入 别名 -> 原名 映射关系
				this.aliasMap.put(alias, name);
				if (logger.isDebugEnabled()) {
					logger.debug("Alias definition '" + alias + "' registered for name '" + name + "'");
				}
			}
		}
	}

	/**
	 * 翻译：返回是否允许别名覆盖
	 *
	 * Return whether alias overriding is allowed.
	 * Default is {@code true}.
	 */
	protected boolean allowAliasOverriding() {
		return true;
	}

	/**
	 * 翻译：确定给定 name 与 alias 是否已经有对应关系
	 * 备注：包含链式关联
	 *
	 * Determine whether the given name has the given alias registered.
	 * @param name the name to check
	 * @param alias the alias to look for
	 * @since 4.2.1
	 */
	public boolean hasAlias(String name, String alias) {
		//循换拿出所有的映射体
		for (Map.Entry<String, String> entry : this.aliasMap.entrySet()) {
			// 获得已注册的原名
			String registeredName = entry.getValue();
			// 如果已注册的原名与输入的原名相同
			if (registeredName.equals(name)) {
				// 取出注册的别名
				String registeredAlias = entry.getKey();
				// 对比别名，如果别名相同，则返回 true
				// 如果别名不同则递归此方法，原名为注册的别名
				if (registeredAlias.equals(alias) || hasAlias(registeredAlias, alias)) {
					//只要能找到别名与原名的链式关联，则返回 true
					return true;
				}
			}
			// 如果注册的原名与输入的原名不同则进行下一次循环
		}
		//如果遍历完所有的映射体，都没有找到链式关联，则返回 false
		return false;
	}

	/** 删除别名 */
	@Override
	public void removeAlias(String alias) {
		// 串行删除
		synchronized (this.aliasMap) {
			// 删除并返回原名
			String name = this.aliasMap.remove(alias);
			// 如果原名不存在，抛出异常
			if (name == null) {
				throw new IllegalStateException("No alias '" + alias + "' registered");
			}
		}
	}

	/** 判断是否是映射表中的别名 */
	@Override
	public boolean isAlias(String name) {
		return this.aliasMap.containsKey(name);
	}

	/** 返回一个原名对应的所有别名 */
	@Override
	public String[] getAliases(String name) {
		List<String> result = new ArrayList<>();
		// 串行获取
		synchronized (this.aliasMap) {
			retrieveAliases(name, result);
		}
		return StringUtils.toStringArray(result);
	}

	/**
	 * 翻译：可传递地检索给定名称的所有别名
	 * 备注：包含链式关联
	 *
	 * Transitively retrieve all aliases for the given name.
	 * @param name the target name to find aliases for
	 * @param result the resulting aliases list
	 */
	private void retrieveAliases(String name, List<String> result) {
		this.aliasMap.forEach((alias, registeredName) -> {
			if (registeredName.equals(name)) {
				result.add(alias);
				retrieveAliases(alias, result);
			}
		});
	}

	/**
	 * 翻译：解析此工厂中注册的所有别名目标名称和别名，并将给定的StringValueResolver应用于它们。
	 *
	 * Resolve all alias target names and aliases registered in this
	 * factory, applying the given StringValueResolver to them.
	 * <p>The value resolver may for example resolve placeholders
	 * in target bean names and even in alias names.
	 * @param valueResolver the StringValueResolver to apply
	 */
	public void resolveAliases(StringValueResolver valueResolver) {
		Assert.notNull(valueResolver, "StringValueResolver must not be null");
		synchronized (this.aliasMap) {
			Map<String, String> aliasCopy = new HashMap<>(this.aliasMap);
			// 循环每一个映射体
			aliasCopy.forEach((alias, registeredName) -> {
				// 使用解析器解析 alias
				String resolvedAlias = valueResolver.resolveStringValue(alias);
				// 使用解析器解析 name
				String resolvedName = valueResolver.resolveStringValue(registeredName);
				// 如果解析后的 alias、name 为空或者两者相同，直接将此别名从 map 中移除
				if (resolvedAlias == null || resolvedName == null || resolvedAlias.equals(resolvedName)) {
					this.aliasMap.remove(alias);
				}
				// 如果解析后的别名与原来的别名不同，
				else if (!resolvedAlias.equals(alias)) {
					// 先查看解析后的 alias 有没有映射的 name
					String existingName = this.aliasMap.get(resolvedAlias);
					// 如果 resolvedAlias 存在映射的 name
					if (existingName != null) {
						// 对比 resolvedAlias 存在映射的 name 与 resolvedName 是否相同
						if (existingName.equals(resolvedName)) {
							// 如果相同，直接删除原别名的映射，因为新的映射已经对应上了
							// Pointing to existing alias - just remove placeholder
							this.aliasMap.remove(alias);
							return;
						}
						// 否则，即 resolvedAlias 不一样，resolvedAlias 映射后的 name 与 resolvedName 也不一样时，直接抛出异常
						// 说通俗点就是：resolvedAlias 已经被其他 name 占用了，resolvedName 无法再次映射
						throw new IllegalStateException(
								"Cannot register resolved alias '" + resolvedAlias + "' (original: '" + alias +
								"') for name '" + resolvedName + "': It is already registered for name '" +
								registeredName + "'.");
					}
					// 如果 resolvedAlias 在映射表中目前还没有映射关系，则准备接入 resolvedAlias -> resolvedName 的映射
					// 再加入新映射之前，检查是否有映射的死循环
					checkForAliasCircle(resolvedName, resolvedAlias);
					// 如果没有死循环则直接删除原来的 alias -> name
					this.aliasMap.remove(alias);
					// 将新的映射关系 resolvedAlias -> resolvedName 加入 map
					this.aliasMap.put(resolvedAlias, resolvedName);
				}
				// 如果解析后的别名与原来的别名相同，并且解析后的name与原来的name不同，则将别名与解析后的 name 映射关联
				else if (!registeredName.equals(resolvedName)) {
					this.aliasMap.put(alias, resolvedName);
				}
				// 剩下的情况就是 alias 与 name 都对应相同
			});
		}
	}

	/**
	 * 翻译：检查给定的名称是否已经指向另一个方向的给定别名，并在前面捕获一个循环引用并抛出相应的 IllegalStateException
	 *
	 * Check whether the given name points back to the given alias as an alias
	 * in the other direction already, catching a circular reference upfront
	 * and throwing a corresponding IllegalStateException.
	 * @param name the candidate name
	 * @param alias the candidate alias
	 * @see #registerAlias
	 * @see #hasAlias
	 */
	protected void checkForAliasCircle(String name, String alias) {
		// 将别名与原名倒过来，查找有没有倒过来的映射关系，如果有，证明会造成循环依赖，即循环依赖
		if (hasAlias(alias, name)) {
			throw new IllegalStateException("Cannot register alias '" + alias +
					"' for name '" + name + "': Circular reference - '" +
					name + "' is a direct or indirect alias for '" + alias + "' already");
		}
	}

	/**
	 * 翻译：确定原始名称，将别名解析为规范名称
	 * 备注：通俗来讲就是，链式查找一个 alias 对应的最根本的 name，传入的参数其实是一个 alias，
	 * 上面 registerAlias 和 resolveAliases 代码中都有检测环状映射的逻辑，这样避免了这个方法的无限循环，因为环状是永远找不到原名的
	 *
	 * Determine the raw name, resolving aliases to canonical names.
	 * @param name the user-specified name
	 * @return the transformed name
	 */
	public String canonicalName(String name) {
		// 重命名：规范名
		String canonicalName = name;
		// 定义：解析名
		String resolvedName;
		do {
			// 将规范名作为参数 alias，寻找是否有对应的原名，并赋值给解析名
			resolvedName = this.aliasMap.get(canonicalName);
			//如果解析名不为空，证明找到了映射关系，则将解析名赋值给规范名
			if (resolvedName != null) {
				canonicalName = resolvedName;
			}
		}
		// 如果解析名不为空时，不停地循环，找到最根本的原名
		while (resolvedName != null);
		// 当解析名为空时，跳出循环，最根本的原名，就是规范名，返回最根本的规范名
		return canonicalName;
	}

}
