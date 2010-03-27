package org.multiverse.transactional;

import org.junit.Test;
import org.multiverse.instrumentation.metadata.ClassMetadata;
import org.multiverse.instrumentation.metadata.FieldMetadata;
import org.multiverse.instrumentation.metadata.MetadataRepository;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Peter Veentjer
 */
public class DefaultTransactionalObjectStructureTest {

    @Test
    public void test(){
        MetadataRepository repository = new MetadataRepository();
        ClassMetadata metadata = repository.getClassMetadata(DefaultTransactionalReference.class);

        assertTrue(metadata.isTransactionalObject());
        assertTrue(metadata.isRealTransactionalObject());
        assertTrue(metadata.hasTransactionalMethods());
        assertTrue(!metadata.hasFieldsWithFieldGranularity());
        assertFalse(metadata.isIgnoredClass());
        assertFalse(metadata.isInterface());

        FieldMetadata value = metadata.getFieldMetadata("value");
        assertTrue(value.isManagedField());
    }
}
