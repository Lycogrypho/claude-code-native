package dev.lain.claudejb.headless

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.lain.claudejb.controller.mcp.tools.code.HierarchyTools

class HierarchyCallerHeadlessTest : BasePlatformTestCase() {

    private fun usages(source: String, call: String): List<PsiElement> {
        val file = myFixture.configureByText("A.java", source)
        return Regex(Regex.escape(call)).findAll(source).map { file.findElementAt(it.range.first)!! }.toList()
    }

    private fun callers(usages: List<PsiElement>): List<String?> =
        usages.map { (HierarchyTools.callerOf(it) as? PsiNamedElement)?.name }

    fun `test a call made from a local variable or a lambda belongs to the method around it`() {
        val usages = usages(SOURCE, "target()").drop(1)
        val nearestOwner = PsiTreeUtil.getParentOfType(usages[1], PsiNameIdentifierOwner::class.java, true)
        assertEquals("the premise: the nearest name owner is the local variable", "local", nearestOwner?.name)
        assertEquals(listOf("field", "caller", "caller", "Inner"), callers(usages))
    }

    fun `test a call made from a method of a nested class belongs to that method`() {
        assertEquals(listOf("nested"), callers(usages(NESTED, "target()").drop(1)))
    }

    private companion object {
        const val SOURCE = """
            class A {
                static int target() { return 1; }
                int field = target();
                void caller() {
                    int local = target();
                    Runnable r = () -> target();
                }
                class Inner { { target(); } }
            }
        """

        const val NESTED = """
            class B {
                static int target() { return 1; }
                static class N { int nested() { return target(); } }
            }
        """
    }
}
