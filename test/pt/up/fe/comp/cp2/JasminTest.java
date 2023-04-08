package pt.up.fe.comp.cp2;

import org.junit.Test;
import pt.up.fe.comp.TestUtils;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.specs.util.SpecsCheck;
import pt.up.fe.specs.util.SpecsIo;
import utils.ProjectTestUtils;

import java.util.Collections;

public class JasminTest {

    @Test
    public void ollirToJasminBasic() {
        testOllirToJasmin("pt/up/fe/comp/cp2/jasmin/OllirToJasminBasic.ollir");
    }

    @Test
    public void ollirToJasminArithmetics() {
        testOllirToJasmin("pt/up/fe/comp/cp2/jasmin/OllirToJasminArithmetics.ollir");
    }

    @Test
    public void ollirToJasminInvoke() {
        testOllirToJasmin("pt/up/fe/comp/cp2/jasmin/OllirToJasminInvoke.ollir");
    }

    @Test
    public void ollirToJasminFields() {
        testOllirToJasmin("pt/up/fe/comp/cp2/jasmin/OllirToJasminFields.ollir");
    }

    @Test
    public void ollirToJasminClassArray() {
        testOllirToJasmin("pt/up/fe/comp/ollir/classArray.ollir");
    }

    @Test
    public void ollirToJasminFac() {
        testOllirToJasmin("pt/up/fe/comp/ollir/Fac.ollir");
    }

    @Test
    public void ollirToJasminIfs() {
        testOllirToJasmin("pt/up/fe/comp/ollir/ifs.ollir");
    }

    @Test
    public void ollirToJasminMyClass1() {
        testOllirToJasmin("pt/up/fe/comp/ollir/myclass1.ollir");
    }

    @Test
    public void ollirToJasminMyClass2() {
        testOllirToJasmin("pt/up/fe/comp/ollir/myclass2.ollir");
    }

    @Test
    public void ollirToJasminMyClass3() {
        testOllirToJasmin("pt/up/fe/comp/ollir/myclass3.ollir");
    }

    @Test
    public void ollirToJasminMyClass4() {
        testOllirToJasmin("pt/up/fe/comp/ollir/myclass4.ollir");
    }


    public static void testOllirToJasmin(String resource, String expectedOutput) {
        SpecsCheck.checkArgument(resource.endsWith(".ollir"), () -> "Expected resource to end with .ollir: " + resource);

        // If AstToJasmin pipeline, change name of the resource and execute other test
        if (TestUtils.hasAstToJasminClass()) {

            // Rename resource
            var jmmResource = SpecsIo.removeExtension(resource) + ".jmm";

            // Test Jmm resource
            var result = TestUtils.backend(SpecsIo.getResource(jmmResource));
            ProjectTestUtils.runJasmin(result, expectedOutput);

            return;
        }

        var ollirResult = new OllirResult(SpecsIo.getResource(resource), Collections.emptyMap());

        var result = TestUtils.backend(ollirResult);

        ProjectTestUtils.runJasmin(result, null);
    }

    public static void testOllirToJasmin(String resource) {
        testOllirToJasmin(resource, null);
    }
}
