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

package lu.kremi151.jector.bean

import lu.kremi151.jector.AutoConfigurator
import lu.kremi151.jector.interfaces.BeanFactory
import java.lang.IllegalStateException
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method

class LazyConfigurableValue<T: Any> (
        private val configurator: AutoConfigurator,
        private val beanFactory: BeanFactory<T>
): InvocationHandler {

    private lateinit var value: T

    override fun invoke(proxy: Any?, method: Method?, args: Array<out Any>?): Any {
        if (!::value.isInitialized) {
            value = beanFactory.create()
            configurator.autoConfigure(value)
        }
        if (method == null) {
            throw IllegalStateException()
        }
        return if (args == null) {
            method.invoke(value)
        } else {
            method.invoke(value, *args)
        }
    }

}