package com.ruoyi.medical.persistence;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** Secret-free operational pin tests only; no deployment files, credentials or service calls. */
class RuntimeObjectStorageBindingTest {
    private static final String REV="11111111-1111-4111-8111-111111111111";
    private static SourceUploadTransport.StorageIdentity identity(String endpoint,String bucket) {
        return new SourceUploadTransport.StorageIdentity("object-service","LOCAL_PRIVATE_OBJECT_STORAGE",endpoint,bucket,"local",REV);
    }
    @Test void exactDeploymentAndBoundsArePartOfIdentity() {
        var a=new RuntimeObjectTransport.StorageBinding(identity("http://127.0.0.1:9000","original"),4096);
        assertEquals(a,new RuntimeObjectTransport.StorageBinding(identity("http://127.0.0.1:9000","original"),4096));
        assertNotEquals(a,new RuntimeObjectTransport.StorageBinding(identity("http://127.0.0.1:9000","other"),4096));
        assertNotEquals(a,new RuntimeObjectTransport.StorageBinding(identity("http://127.0.0.1:9001","original"),4096));
        assertNotEquals(a,new RuntimeObjectTransport.StorageBinding(identity("http://127.0.0.1:9000","original"),8192));
    }
    @Test void credentialsAndQueryTokensCannotEnterPersistedIdentity() {
        for(String endpoint:java.util.List.of("http://secret:password@127.0.0.1:9000","http://127.0.0.1:9000?token=secret","http://127.0.0.1:9000#secret"))
            assertThrows(IllegalArgumentException.class,()->identity(endpoint,"original"));
    }
}
