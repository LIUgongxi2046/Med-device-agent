package com.ruoyi.medical.persistence;
import java.util.*;
/** Product acceptance requirements from PRD 8.5, not newly asserted legal requirements. */
final class RegistrationDossierRequirements {
 private RegistrationDossierRequirements(){}
 static Map<String,String> firstDirectory()throws java.io.IOException{
  var m=new LinkedHashMap<String,String>();for(var n:RegistrationDirectory.fromClasspath().catalogue().nodes())m.put(n.officialNumber(),n.title());
  m.put("6.7.1","设计开发");m.put("6.7.2","采购");m.put("6.7.3","生产服务");m.put("6.7.4","监视测量设备");return m;
 }
 static final Set<String> CONDITIONS=Set.of("SOFTWARE","STERILIZATION","BODY_CONTACT","CLEANROOM","OUTSOURCING","PRIOR_INSPECTION");
 static boolean procedure(String code){return Set.of("6.4","6.5","6.6","6.7","6.7.1","6.7.2","6.7.3","6.7.4","6.8").contains(code);}
 static final Map<String,List<String>> FIELDS=Map.ofEntries(
  Map.entry("1.2",List.of("authority_form_version","applicant","agent","application_matter")),
  Map.entry("1.3",List.of("terms_and_abbreviations")),Map.entry("1.4",List.of("product_models_components_identifiers")),
  Map.entry("1.5",List.of("related_documents_subject_validity")),Map.entry("1.6",List.of("communication_history_or_nonapplicability")),
  Map.entry("1.7",List.of("regulatory_conformity","classification_basis","standards_editions","authenticity_statement","authorized_signatory")),
  Map.entry("2.2",List.of("generic_name_basis","classification_and_code","intended_use","application_background")),
  Map.entry("2.3",List.of("principle","composition_materials_components","software_interfaces_delivery","model_configuration_function_parameter_differences","packaging_barrier_integrity","development_comparison_evidence")),
  Map.entry("2.4",List.of("function_disease_stage_population_site","user_environment_reuse_combination","contraindications_limitations_warnings")),
  Map.entry("2.5",List.of("country_dates_sales","adverse_events_recalls_measures","data_period_and_calculation_basis")),
  Map.entry("2.6",List.of("companion_products_certificates_compatibility","other_explanations")),
  Map.entry("6.1",List.of("quality_system_establishment_and_inspection_commitment")),
  Map.entry("6.3",List.of("principle_and_overall_process","all_production_sites_contacts","outsourcing_and_suppliers","critical_materials_process_sterilization_controls")),
  Map.entry("6.4",List.of("quality_manual","quality_policy","quality_objectives","document_control","record_control")),
  Map.entry("6.5",List.of("planning","responsibility_authority_communication","management_review")),
  Map.entry("6.6",List.of("human_resources","infrastructure","work_environment")),
  Map.entry("6.7",List.of("product_realization_planning_customer_process")),
  Map.entry("6.7.1",List.of("design_development_transfer")),Map.entry("6.7.2",List.of("purchasing_quality_requirements")),
  Map.entry("6.7.3",List.of("production_service_cleanliness_installation_validation_identification_traceability")),
  Map.entry("6.7.4",List.of("monitoring_measurement_equipment_control")),
  Map.entry("6.8",List.of("conformity_effectiveness_monitoring_measurement_analysis_improvement")),
  Map.entry("6.9",List.of("other_related_quality_information")),
  Map.entry("6.10",List.of("enterprise_information","organization_chart","site_area_layout","cleanroom_reports_layout_or_nonapplicability","process_control_points","material_component_sources_quality_controls","production_inspection_environment_equipment","self_inspection_report","previous_inspection_comparison_or_nonapplicability")));
}
