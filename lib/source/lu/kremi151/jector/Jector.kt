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
import lu.kremi151.jector.bean.*
import lu.kremi151.jector.enums.Priority
import lu.kremi151.jector.interfaces.BeanFactory
import lu.kremi151.jector.interfaces.DependencyManager
import java.lang.reflect.Field

class Jector(
        private val dependencyManager: DependencyManager
): AutoConfigurator {

    constructor(allowNonInterfaces: Boolean): this(JectorDependencyManager(allowNonInterfaces))
    constructor(): this(false)

    init {
        dependencyManager.collectProviders(
                listOf(StaticBeanFactory(AutoConfigurator::class.java, this)),
                Priority.HIGHEST,
                false
        )
    }

    fun collectProviders(obj: Any) {
        dependencyManager.collectProviders(obj)
    }

    fun collectProviders(factories: List<BeanFactory<*>>, priority: Priority, lazy: Boolean) {
        dependencyManager.collectProviders(factories, priority, lazy)
    }

    fun initializeBeans() {
        dependencyManager.initializeBeans(this)
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

    private fun autoConfigureField(obj: Any, field: Field, annotation: Inject) {
        dependencyManager.autoConfigureField(obj, field, annotation)
    }

}