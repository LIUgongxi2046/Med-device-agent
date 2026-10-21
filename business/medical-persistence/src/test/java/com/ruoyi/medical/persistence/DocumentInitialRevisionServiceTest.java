package com.ruoyi.medical.persistence;

import static org.junit.jupiter.api.Assertions.*;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import java.lang.reflect.Modifier;
import java.util.*;
import org.junit.jupiter.api.Test;

class DocumentInitialRevisionServiceTest {
    private static final String TEMPLATE="10000000-0000-4000-8000-000000000001", DOCUMENT="10000000-0000-4000-8000-000000000002", FIRST="10000000-0000-4000-8000-000000000003", SECOND="10000000-0000-4000-8000-000000000004", FIELD="10000000-0000-4000-8000-000000000005";
    private TemplateProtocol.Node node(String id,String parent,int ordinal){return new TemplateProtocol.Node(id,parent,"1","Section",ordinal,null,true,"NARRATIVE",null,List.of(),"REQUIRED",List.of());}
    private TemplateProtocol.Content template(List<TemplateProtocol.Node> nodes){
        var constraints=new TemplateProtocol.Constraints(100,null,null,null,null,List.of(),List.of(),List.of());
        var field=new TemplateProtocol.Field(FIELD,FIRST,"name","Name","", "TEXT",constraints,null,true,null,new TemplateProtocol.OutputMapping(FIRST,null,null,false,0),null,null,List.of());
        return new TemplateProtocol.Content("1.0.0","Test document","TEST",false,"TEST",nodes,List.of(field),List.of(),List.of(),List.of(),null,List.of(),null);
    }
    @Test void blankHasRealTemplateNodesAndExplicitEmptyScaffoldWithoutClaims()throws Exception{
        var json=new TaskSectionWriterSchema();var body=DocumentInitialRevisionService.blank(json,template(List.of(node(SECOND,FIRST,0),node(FIRST,null,0))),TEMPLATE,DOCUMENT);
        DocumentBodySemantics.verify(SchemaCatalogue.fromClasspath(),body,new DocumentBodySemantics.Limits(20,20,100));
        assertEquals(DOCUMENT,body.required("nodes").get(0).required("node_id").asText());
        assertEquals(FIRST,body.required("nodes").get(1).required("node_id").asText());
        assertEquals(SECOND,body.required("nodes").get(2).required("node_id").asText());
        assertEquals("",body.required("fields").get(0).required("original_value").asText());
        assertTrue(body.required("fields").get(0).required("decimal_value").isNull());
        assertTrue(body.required("evidence_links").isEmpty());assertTrue(body.required("fact_usages").isEmpty());
        assertEquals(body,DocumentInitialRevisionService.blank(json,template(List.of(node(SECOND,FIRST,0),node(FIRST,null,0))),TEMPLATE,DOCUMENT));
    }
    @Test void explicitV2ScaffoldRequiresTheV2PublicationAndNeverRelabelsLegacy()throws Exception{
        var json=new TaskSectionWriterSchema();var template=template(List.of(node(FIRST,null,0)));var catalogue=SchemaCatalogue.fromClasspath();
        var legacy=DocumentInitialRevisionService.blank(json,template,TEMPLATE,DOCUMENT);
        var v2=DocumentInitialRevisionService.blank(json,template,TEMPLATE,DOCUMENT,"2.0.0");
        assertEquals("1.0.0",legacy.required("schema_version").asText());assertEquals("2.0.0",v2.required("schema_version").asText());
        DocumentBodySemantics.verify(catalogue,v2,new DocumentBodySemantics.Limits(20,20,100),catalogue.schemaRef("document-body-v2","DocumentBody"),catalogue.schemaDigest("document-body-v2"));
        assertThrows(RuntimeException.class,()->DocumentBodySemantics.verify(catalogue,v2,new DocumentBodySemantics.Limits(20,20,100)));
        assertThrows(RuntimeException.class,()->DocumentBodySemantics.verify(catalogue,legacy,new DocumentBodySemantics.Limits(20,20,100),catalogue.schemaRef("document-body-v2","DocumentBody"),catalogue.schemaDigest("document-body-v2")));
    }
    @Test void cycleCannotProduceAnApparentlyValidBlank(){assertThrows(TenantAccessException.class,()->DocumentInitialRevisionService.blank(new TaskSectionWriterSchema(),template(List.of(node(FIRST,SECOND,0),node(SECOND,FIRST,0))),TEMPLATE,DOCUMENT));}
    @Test void businessRevisionAndInitialAuthorityCannotBeCallerConstructed(){
        for(var type:List.of(DocumentInitialRevisionService.Verified.class,DocumentInitialRevisionService.Prepared.class,DocumentInitialRevisionService.Attempt.class,TaskSectionWriterProducerAuthority.Initial.class))
            for(var constructor:type.getDeclaredConstructors())assertTrue(Modifier.isPrivate(constructor.getModifiers()));
    }
    @Test void chapterIdsAreStableAndDistinctWithoutPretendingToBeDocumentRevision(){
        assertEquals(TaskSectionWriterPreparation.stepId(DOCUMENT,FIRST),TaskSectionWriterPreparation.stepId(DOCUMENT,FIRST));
        assertNotEquals(TaskSectionWriterPreparation.stepId(DOCUMENT,FIRST),TaskSectionWriterPreparation.stepId(DOCUMENT,SECOND));
        assertNotEquals(TaskSectionWriterPreparation.stepId(DOCUMENT,FIRST),TaskSectionWriterPreparation.inputId(DOCUMENT,FIRST));
    }
}
