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
import lu.kremi151.jector.enums.Priority
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

@Suppress("UNUSED")
class JectorTest {

    private lateinit var configuration: Any
    private lateinit var aImpl1: AInterface
    private lateinit var aImpl2: AInterface
    private lateinit var bImpl1: BInterface
    private lateinit var bImpl2: BInterface
    private lateinit var cImpl1: CInterface

    @BeforeEach
    fun beforeEachTest() {
        aImpl1 = object : AInterface {
            override fun toString(): String {
                return "AInterface@aImpl1"
            }
            override fun getValue(): String {
                return "ABC"
            }
        }
        aImpl2 = object : AInterface {
            override fun toString(): String {
                return "AInterface@aImpl2"
            }
            override fun getValue(): String {
                return "DEF"
            }
        }
        bImpl1 = object : BInterface {
            override fun toString(): String {
                return "BInterface@bImpl1"
            }
            override fun getSomething(): String {
                return "abc"
            }
        }
        bImpl2 = object : BInterface {
            override fun toString(): String {
                return "BInterface@bImpl2"
            }
            override fun getSomething(): String {
                return "def"
            }
        }
        cImpl1 = object : CInterface {
            override fun toString(): String {
                return "Jeremy@Bearimy"
            }
            override fun takeItSleazy(): Boolean {
                return true
            }
        }
        configuration = object {
            @Provider(priority = Priority.LOWEST) fun createImplA1(): AInterface {
                return aImpl1
            }
            @Provider(priority = Priority.HIGHEST) fun createImplB1(): BInterface {
                return bImpl1
            }
            @Provider fun createImpA2(): AInterface {
                return aImpl2
            }
            @Provider(priority = Priority.LOW) fun createImplB2(): BInterface {
                return bImpl2
            }
            @Provider fun createTGP(): CInterface {
                return cImpl1
            }
        }
    }

    @Test
    fun testBasicInjection() {
        val configurator = Jector()
        configurator.collectProviders(configuration)
        configurator.initializeBeans()
        val configurableObject = object {
            @Inject lateinit var implA: AInterface
            @Inject lateinit var implB: BInterface
            @Inject lateinit var implC: CInterface
            @Inject lateinit var supz: SuperInterface
        }
        configurator.autoConfigure(configurableObject)
        assertSame(aImpl2, configurableObject.implA)
        assertSame(bImpl1, configurableObject.implB)
        assertSame(cImpl1, configurableObject.implC)
        assertSame(bImpl1, configurableObject.supz)
    }

    @Test
    fun testListInjection() {
        val configurator = Jector()
        configurator.collectProviders(configuration)
        configurator.initializeBeans()
        val configurableObject = object {
            @Inject(collectionType = AInterface::class)
            lateinit var listA: List<AInterface>
            @Inject(collectionType = BInterface::class)
            lateinit var listB: List<BInterface>
            @Inject(collectionType = CInterface::class)
            lateinit var listC: List<CInterface>
            @Inject(collectionType = SuperInterface::class)
            lateinit var listS: List<SuperInterface>
        }
        configurator.autoConfigure(configurableObject)
        assertIterableEquals(listOf(aImpl2, aImpl1), configurableObject.listA)
        assertIterableEquals(listOf(bImpl1, bImpl2), configurableObject.listB)
        assertIterableEquals(listOf(cImpl1), configurableObject.listC)
        assertIterableEquals(listOf(bImpl1, aImpl2, bImpl2, aImpl1), configurableObject.listS)
    }

    @Test
    fun testLazyProvider() {
        var eagerBeanCreated = false
        var lazyBeanCreated = false
        val configuration = object {
            @Provider fun createEagerImpl(): AInterface {
                eagerBeanCreated = true
                return aImpl1
            }
            @Provider(lazy = true) fun createLazyImpl(): BInterface {
                lazyBeanCreated = true
                return bImpl1
            }
        }
        val configurator = Jector()
        configurator.collectProviders(configuration)
        assertFalse(eagerBeanCreated)
        assertFalse(lazyBeanCreated)
        configurator.initializeBeans()
        assertTrue(eagerBeanCreated)
        assertFalse(lazyBeanCreated)
        val configurableObject = object {
            @Inject lateinit var implA: AInterface
            @Inject lateinit var implB: BInterface
        }
        configurator.autoConfigure(configurableObject)
        assertTrue(eagerBeanCreated)
        assertFalse(lazyBeanCreated)
        assertSame(aImpl1, configurableObject.implA)
        assertNotSame(bImpl1, configurableObject.implB)
        assertEquals(aImpl1.getValue(), configurableObject.implA.getValue())
        assertEquals(bImpl1.getSomething(), configurableObject.implB.getSomething())
        assertTrue(eagerBeanCreated)
        assertTrue(lazyBeanCreated)
    }

    @Test
    fun testNestedDependencyInjection() {
        val aImpl = object : AInterface {
            @Inject lateinit var b: BInterface
            override fun getValue(): String {
                return "_aBc_"
            }
        }
        val bImpl = object : BInterface {
            @Inject lateinit var c: CInterface
            override fun getSomething(): String {
                return "_dEf_"
            }
        }
        val cImpl = object : CInterface {
            @Inject lateinit var a: AInterface
            override fun takeItSleazy(): Boolean {
                return true
            }
        }
        val configuration = object {
            @Provider fun createAInterfaceImpl(): AInterface {
               return aImpl
            }
            @Provider fun createBInterfaceImpl(): BInterface {
                return bImpl
            }
            @Provider fun createCInterfaceImpl(): CInterface {
                return cImpl
            }
        }
        val configurator = Jector()
        configurator.collectProviders(configuration)
        configurator.initializeBeans()
        assertSame(bImpl, aImpl.b)
        assertSame(cImpl, bImpl.c)
        assertSame(aImpl, cImpl.a)
    }

    @Test
    fun testInheritanceInjection() {
        val appleJuiceImpl = AppleJuice(128)
        val configuration = object {
            @Provider fun createAppleJuice(): AppleJuice {
               return appleJuiceImpl
            }
        }
        val configurator = Jector()
        configurator.collectProviders(configuration)
        configurator.initializeBeans()
        val configurableObject = object {
            @Inject lateinit var fruit: Fruit
            @Inject lateinit var apple: Apple
            @Inject lateinit var juice: AppleJuice
        }
        configurator.autoConfigure(configurableObject)
        assertSame(appleJuiceImpl, configurableObject.fruit)
        assertSame(appleJuiceImpl, configurableObject.apple)
        assertSame(appleJuiceImpl, configurableObject.juice)
    }

    @Test
    fun testProviderDetectionFromBaseClasses() {
        val aImpl = object : AInterface {
            override fun getValue(): String {
                return "_aBc_"
            }
        }
        val cImpl = object : CInterface {
            override fun takeItSleazy(): Boolean {
                return true
            }
        }
        val childClassProvider = ChildClassProvider(aImpl, cImpl)
        val configurator = Jector()
        configurator.collectProviders(childClassProvider)
        configurator.initializeBeans()
        val configurableObject = object {
            @Inject lateinit var aImpl: AInterface
            @Inject lateinit var cImpl: CInterface
        }
        configurator.autoConfigure(configurableObject)
        assertSame(aImpl, configurableObject.aImpl)
        assertSame(cImpl, configurableObject.cImpl)
    }

    @Test
    fun testInjectionIntoBaseClasses() {
        val aImpl = object : AInterface {
            override fun getValue(): String {
                return "_aBc_"
            }
        }
        val cImpl = object : CInterface {
            override fun takeItSleazy(): Boolean {
                return true
            }
        }
        val fruit = Apple(420)
        val configuration = object {
            @Provider fun createAInterfaceImpl(): AInterface {
                return aImpl
            }
            @Provider fun createCInterfaceImpl(): CInterface {
                return cImpl
            }
            @Provider fun createApple(): Apple {
                return fruit
            }
        }
        val configurator = Jector()
        configurator.collectProviders(configuration)
        configurator.initializeBeans()
        val configurableObject = ChildClassInjectable()
        configurator.autoConfigure(configurableObject)
        assertSame(aImpl, configurableObject.aImpl)
        assertSame(cImpl, configurableObject.cImpl)
        assertSame(fruit, configurableObject.getFruit())
    }

    interface SuperInterface

    interface AInterface: SuperInterface {
        fun getValue(): String
    }

    interface BInterface: SuperInterface {
        fun getSomething(): String
    }

    interface CInterface {
        fun takeItSleazy(): Boolean
    }

    open class Fruit(val tastyness: Int)
    open class Apple(tastyness: Int): Fruit(tastyness)
    class AppleJuice(tastyness: Int): Apple(tastyness)

    open class SuperClassProvider(private val aImpl: AInterface) {
        @Provider fun createAInterface(): AInterface {
            return aImpl
        }
    }

    class ChildClassProvider(aImpl: AInterface, private val cImpl: CInterface): SuperClassProvider(aImpl) {
        @Provider fun createCInterface(): CInterface {
            return cImpl
        }
    }

    open class SuperClassInjectable {
        @Inject lateinit var aImpl: AInterface
        @Inject private lateinit var fruit: Fruit

        fun getFruit(): Fruit {
            return fruit
        }
    }

    class ChildClassInjectable: SuperClassInjectable() {
        @Inject lateinit var cImpl: CInterface
    }

}