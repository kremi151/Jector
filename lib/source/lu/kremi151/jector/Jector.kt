/**
 * Copyright 2020 Michel Kremer (kremi151)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package lu.kremi151.jector

import lu.kremi151.jector.annotations.Inject
import lu.kremi151.jector.annotations.Provider
import lu.kremi151.jector.bean.BeanEntry
import lu.kremi151.jector.bean.FactoryEntry
import lu.kremi151.jector.bean.InstantiatedBean
import lu.kremi151.jector.bean.LazyConfigurableValue
import lu.kremi151.jector.enums.Priority
import java.lang.IllegalStateException
import java.lang.reflect.Field
import java.lang.reflect.Proxy
import java.util.stream.Collectors
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class Jector: AutoConfigurator {

    private val factories: MutableMap<Class<*>, MutableList<FactoryEntry>> = HashMap()
    private val allBeans: MutableList<BeanEntry<*>> = ArrayList()
    private val primaryBeans: MutableMap<Class<*>, Any> = HashMap()

    init {
        val beanEntry = BeanEntry(this, Jector::class.java, Priority.HIGHEST)
        allBeans.add(beanEntry)
        primaryBeans[AutoConfigurator::class.java] = arrayListOf(beanEntry)
    }

    private fun addFactory(type: Class<*>, entry: FactoryEntry) {
        var factoriesList = factories[type]
        if (factoriesList == null) {
            factoriesList = ArrayList()
            factories[type] = factoriesList
        }
        factoriesList.add(entry)
        factoriesList.sortBy { it.priority.ordinal }
    }

    fun collectProviders(obj: Any) {
        val methods = obj.javaClass.methods
        for (method in methods) {
            val providerMeta = method.getAnnotation(Provider::class.java) ?: continue
            if (method.parameterCount != 0) {
                throw IllegalStateException("Provider factory $method has ${method.parameterCount} parameters, but there should be none")
            }
            val primaryType = method.returnType
            if (AutoConfigurator::class.java.isAssignableFrom(primaryType)) {
                throw IllegalStateException("Providers of type ${AutoConfigurator::class.java} cannot be manually defined")
            }

            val factoryEntry = FactoryEntry(obj, method, providerMeta.priority, providerMeta.lazy)
            var type: Class<*>? = primaryType
            while (type != null && type != Object::class.java) {
                addFactory(type, factoryEntry)
                type = type.superclass
            }
        }
    }

    fun initializeBeans() {
        val classToBean = HashMap<Class<*>, InstantiatedBean<*>>()//TODO: Store method reference to know which collisions to ignore
        for (entry in factories) {
            val factories = entry.value
            if (factories.isEmpty()) {
                continue
            }
            for (factory in factories) {
                if (factory.lazy) {
                    // TODO: Add check for conflicts
                    val bean = Proxy.newProxyInstance(
                            factory.holder.javaClass.classLoader,
                            arrayOf(factory.method.returnType),
                            LazyConfigurableValue<Any>(this, factory.holder, factory.method)
                    )
                    classToBean[bean.javaClass] = InstantiatedBean(bean, factory.method)
                    allBeans.add(BeanEntry(bean, bean.javaClass, factory.priority))
                } else {
                    val bean = factory.method.invoke(factory.holder)
                    val existingInstance = classToBean[bean.javaClass]
                    if (existingInstance != null) {
                        if (existingInstance.method == factory.method) {
                            // Same factory method, therefore no collision
                            continue
                        } else {
                            throw IllegalStateException("Conflicting providers for type ${bean.javaClass}")
                        }
                    }
                    classToBean[bean.javaClass] = InstantiatedBean(bean, factory.method)
                    allBeans.add(BeanEntry(bean, bean.javaClass, factory.priority))
                }
            }
        }
        for (bean in allBeans) {
            autoConfigure(bean.bean!!)
        }
    }

    private fun getAllFields(clazz: Class<*>, outSet: MutableSet<Field>) {
        var currentClass: Class<*>? = clazz
        while (currentClass != null && currentClass != Object::class.java) {
            outSet.addAll(currentClass.declaredFields)
            currentClass = currentClass.superclass
        }
    }

    override fun autoConfigure(obj: Any) {
        val fields = HashSet<Field>()
        getAllFields(obj.javaClass, fields)
        for (field in fields) {
            val annotation = field.getAnnotation(Inject::class.java) ?: continue
            autoConfigureField(obj, field, annotation)
        }

        // TODO: Inject configurations
    }

    private fun <T> getConfigurableValue(type: Class<T>): T? {
        // Check if we have a direct match
        var match = primaryBeans[type]
        if (match != null) {
            @Suppress("UNCHECKED_CAST")
            return match as T?
        }

        // Scan through the existing beans and find the one with the highest priority
        var maxPriority: Priority? = null
        for (bean in allBeans) {
            if (!type.isAssignableFrom(bean.beanType)) {
                continue
            }
            if (maxPriority == null || maxPriority.ordinal < bean.priority.ordinal) {
                maxPriority = bean.priority
                match = bean.bean
            }
        }
        if (match != null) {
            // Cache result for quicker lookup
            primaryBeans[type] = match

            @Suppress("UNCHECKED_CAST")
            return match as T?
        }
        return null
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> getPriorizedConfigurableList(collectionType: Class<T>): List<T> {
        return allBeans.stream()
                .filter { entry -> collectionType.isAssignableFrom(entry.beanType) }
                .sorted(compareByDescending { it.priority.ordinal })
                .map { entry -> entry.bean }
                .collect(Collectors.toList()) as List<T>
    }

    private fun autoConfigureField(obj: Any, field: Field, annotation: Inject) {
        field.isAccessible = true

        if (List::class.java.isAssignableFrom(field.type)) {
            if (annotation.collectionType == Any::class) {
                throw IllegalStateException("@Inject annotations for a List in field $field does not specify a collection type")
            }
            val list = getPriorizedConfigurableList(annotation.collectionType.java)
            field.set(obj, list)
            return
        }

        val match = getConfigurableValue(field.type)
        if (match != null) {
            field.set(obj, match)
            return
        }

        throw IllegalStateException("Could not inject value at $field")
    }

}