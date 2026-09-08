package yourscraft.jasdewstarfield.brnquest.editor;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class IntegerVectorValueTest {
    @Test void malformedAndPartialVectorsStayInvalidWithoutLosingData() {
        assertArrayEquals(new int[]{-1,2,3},IntegerVectorValue.parse("-1, 2,3"));
        assertEquals(List.of("1,2,3,4","",""),IntegerVectorValue.components("1,2,3,4"));
        for(String value:List.of("1,,3", "1,2,3,4", "1,2.5,3", "2147483648,0,0"))
            assertThrows(IllegalArgumentException.class,()->IntegerVectorValue.parse(value));
        var size=ConfigFieldDescriptor.field("size",ConfigValueType.INTEGER_VECTOR3).withRange(1,Integer.MAX_VALUE);
        assertFalse(ConfigEditorSchemas.validate(List.of(size),Map.of("size","1,0,1")).isEmpty());
        assertTrue(ConfigEditorSchemas.validate(List.of(size),Map.of("size","1,2,3")).isEmpty());
    }
}
