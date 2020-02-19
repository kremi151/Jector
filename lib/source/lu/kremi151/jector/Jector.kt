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
import lu.kremi151.jector.bean.*
import lu.kremi151.jector.enums.Priority
import lu.kremi151.jector.exception.NotAnInterfaceException
import lu.kremi151.jector.interfaces.BeanFactory
import java.lang.IllegalStateException
import java.lang.reflect.Field
import java.lang.reflect.Proxy
import java.util.stream.Collectors
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class Jector(
        private val allowNonInterfaces: Boolean
): AutoConfigurator {

    private val factories: MutableMap<Class<*>, MutableList<FactoryEntry>> = HashMap()
    private val allBeans: MutableList<BeanEntry<*>> = ArrayList()
    private val primaryBeans: MutableMap<Class<*>, Any> = HashMap()

    constructor(): this(false)

    init {
        val beanEntry = BeanEntry(this, Jector::class.java, Priority.HIGHEST)
        allBeans.add(beanEntry)
        primaryBeans[AutoConfigurator::class.java] = arrayListOf(beanEntry)
    }

    private fun addFactory(type: Class<*>, entry: FactoryEntry): Boolean {
        if (!allowNonInterfaces && !type.isInterface) {
            return false
        }
        var factoriesList = factories[type]
        if (factoriesList == null) {
            factoriesList = ArrayList()
            factories[type] = factoriesList
        }
        factoriesList.add(entry)
        factoriesList.sortBy { it.priority.ordinal }
        return true
    }

    private fun addFactoryRecursively(factory: BeanFactory<*>, primaryType: Class<*>, priority: Priority, lazy: Boolean) {
        val factoryEntry = FactoryEntry(factory, priority, lazy)
        var type: Class<*>? = primaryType
        var added = false
        while (type != null && type != Object::class.java) {
            added = addFactory(type, factoryEntry) || added
            type = type.superclass
        }
        if (!added) {
            throw NotAnInterfaceException("Class $primaryType does not implement an interface. Only implementations of interfaces can act as a provider for the current Jector instance.")
        }
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

            @Suppress("UNCHECKED_CAST")
            val beanFactory = BeanReflectionFactory(obj, method, method.returnType as Class<Any>)
            addFactoryRecursively(beanFactory, primaryType, providerMeta.priority, providerMeta.lazy)
        }
    }

    fun collectProviders(factories: List<BeanFactory<*>>, priority: Priority, lazy: Boolean) {
        for (factory in factories) {
            val primaryType = factory.returnType
            if (AutoConfigurator::class.java.isAssignableFrom(primaryType)) {
                throw IllegalStateException("Providers of type ${AutoConfigurator::class.java} cannot be manually defined")
            }

            addFactoryRecursively(factory, primaryType, priority, lazy)
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
                            factory.factory.holderClassLoader,
                            arrayOf(factory.factory.returnType),
                            @Suppress("UNCHECKED_CAST")
                            LazyConfigurableValue(this, factory.factory as BeanFactory<Any>)
                    )
                    classToBean[bean.javaClass] = InstantiatedBean(bean, factory.factory)
                    allBeans.add(BeanEntry(bean, bean.javaClass, factory.priority))
                } else {
                    val bean = factory.factory.create()!!
                    val existingInstance = classToBean[bean.javaClass]
                    if (existingInstance != null) {
                        if (existingInstance.factory == factory.factory) {
                            // Same factory method, therefore no collision
                            continue
                        } else {
                            throw IllegalStateException("Conflicting providers for type ${bean.javaClass}")
                        }
                    }
                    @Suppress("UNCHECKED_CAST")
                    classToBean[bean.javaClass] = InstantiatedBean(bean, factory.factory as BeanFactory<Any>)
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

        if (!allowNonInterfaces && !field.type.isInterface) {
            throw NotAnInterfaceException("The type of field $field is ${field.type}, which is not an interface. Only implementations of interfaces can be injected by the current Jector instance.")
        }

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