package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;

final class SimulationModelConfigurationFixtures {
    static final String CASE = "a8142a6f-c813-45e0-b68b-df9743474915";
    static final String PROVENANCE_HASH = "ba6ee6a53f72ed4f62bec2243e35853706add8bf4e24e272b441fa50ef6dbc4c";
    static ModelConfigurationProtocol.SimulationDefinition definition() {
        return new ModelConfigurationProtocol.SimulationDefinition("SIMULATION", "medical_model_gateway.simulation.SimulationProvider", "1.0.0",
                "SIMULATION:fixture", "SIMULATION:v1", new ModelConfigurationProtocol.CaseBinding(CASE, "a".repeat(64), "DECLARED_ONLY"),
                new ModelConfigurationProtocol.Limits("128000", "8000", "2", "10", "100000", 60, false, List.of("none"), 0),
                "urn:medical:local-simulation-provenance:1.0.0", PROVENANCE_HASH, false, "0.00000000", "LOCAL_SIMULATION_NO_PROVIDER_REQUEST");
    }
    static ObjectNode request(PermissionSchema json) {
        return (ObjectNode) json.tree(new ModelConfigurationProtocol.Save("1.0.0", null, null, "0", null, definition(), "SIMULATION draft binding; no execution evidence"));
    }
}
