package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.InterfaceProtocol;

/** Object I/O only: the caller must persist and bind the original intent before calling. */
@FunctionalInterface
public interface SimulationResultTransport {
    InterfaceProtocol.ObjectRef write(String tenantId, String actorId, S3ObjectWriter.WriteIntent intent,
        byte[] canonicalBytes, boolean recover, S3ObjectReader.Admission current) throws Exception;
}
