/*
 * Copyright 2013-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.jpa.repository.config;

import static org.springframework.data.jpa.domain.support.AuditingBeanFactoryPostProcessor.*;
import static org.springframework.data.jpa.repository.config.BeanDefinitionNames.*;

import java.lang.annotation.Annotation;

import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.aspectj.AnnotationBeanConfigurerAspect;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.data.auditing.config.AuditingBeanDefinitionRegistrarSupport;
import org.springframework.data.auditing.config.AuditingConfiguration;
import org.springframework.data.config.ParsingUtils;
import org.springframework.data.jpa.domain.support.AuditingBeanFactoryPostProcessor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.data.mapping.context.PersistentEntities;
import org.springframework.data.repository.config.PersistentEntitiesFactoryBean;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * {@link ImportBeanDefinitionRegistrar} to enable {@link EnableJpaAuditing} annotation.
 *
 * @author Thomas Darimont
 * @author Christoph Strobl
 */
class JpaAuditingRegistrar extends AuditingBeanDefinitionRegistrarSupport {

	private static final String BEAN_CONFIGURER_ASPECT_CLASS_NAME = "org.springframework.beans.factory.aspectj.AnnotationBeanConfigurerAspect";

	@Override
	protected Class<? extends Annotation> getAnnotation() {
		return EnableJpaAuditing.class;
	}

	@Override
	protected String getAuditingHandlerBeanName() {
		return "jpaAuditingHandler";
	}

	@Override
	public void registerBeanDefinitions(AnnotationMetadata annotationMetadata, BeanDefinitionRegistry registry) {

		Assert.notNull(annotationMetadata, "AnnotationMetadata must not be null");
		Assert.notNull(registry, "BeanDefinitionRegistry must not be null");

		registerBeanConfigurerAspectIfNecessary(registry);
		super.registerBeanDefinitions(annotationMetadata, registry);
		registerInfrastructureBeanWithId(
				BeanDefinitionBuilder.rootBeanDefinition(AuditingBeanFactoryPostProcessor.class).getRawBeanDefinition(),
				AuditingBeanFactoryPostProcessor.class.getName(), registry);
	}

	@Override
	protected void registerAuditListenerBeanDefinition(BeanDefinition auditingHandlerDefinition,
			BeanDefinitionRegistry registry) {

		if (!registry.containsBeanDefinition(JPA_MAPPING_CONTEXT_BEAN_NAME)) {
			registry.registerBeanDefinition(JPA_MAPPING_CONTEXT_BEAN_NAME, //
					new RootBeanDefinition(JpaMetamodelMappingContextFactoryBean.class));
		}

		BeanDefinitionBuilder builder = BeanDefinitionBuilder.rootBeanDefinition(AuditingEntityListener.class);
		builder.addPropertyValue("auditingHandler",
				ParsingUtils.getObjectFactoryBeanDefinition(getAuditingHandlerBeanName(), null));
		registerInfrastructureBeanWithId(builder.getRawBeanDefinition(), AuditingEntityListener.class.getName(), registry);
	}

	@Override
	protected void postProcess(BeanDefinitionBuilder builder, AuditingConfiguration configuration,
			BeanDefinitionRegistry registry) {

		String persistentEntitiesBeanName = detectPersistentEntitiesBeanName(registry);

		if (persistentEntitiesBeanName == null) {

			persistentEntitiesBeanName = BeanDefinitionReaderUtils.uniqueBeanName("jpaPersistentEntities", registry);

			// TODO: https://github.com/spring-projects/spring-framework/issues/28728
			BeanDefinitionBuilder definition = BeanDefinitionBuilder.genericBeanDefinition(PersistentEntities.class) //
					.setFactoryMethod("of") //
					.addConstructorArgReference(JPA_MAPPING_CONTEXT_BEAN_NAME);

			registry.registerBeanDefinition(persistentEntitiesBeanName, definition.getBeanDefinition());
		}

		builder.addConstructorArgReference(persistentEntitiesBeanName);
	}

	@Nullable
	private static String detectPersistentEntitiesBeanName(BeanDefinitionRegistry registry) {

		if (registry instanceof ListableBeanFactory beanFactory) {
			for (String bn : beanFactory.getBeanNamesForType(PersistentEntities.class)) {
				if (bn.startsWith("jpa")) {
					return bn;
				}
			}
		}

		return null;
	}


	/**
	 * @param registry, the {@link BeanDefinitionRegistry} to be used to register the
	 *          {@link AnnotationBeanConfigurerAspect}.
	 */
	private void registerBeanConfigurerAspectIfNecessary(BeanDefinitionRegistry registry) {

		if (registry.containsBeanDefinition(BEAN_CONFIGURER_ASPECT_BEAN_NAME)) {
			return;
		}

		if (!ClassUtils.isPresent(BEAN_CONFIGURER_ASPECT_CLASS_NAME, getClass().getClassLoader())) {
			throw new BeanDefinitionStoreException(BEAN_CONFIGURER_ASPECT_CLASS_NAME + " not found; \n"
					+ "Could not configure Spring Data JPA auditing-feature because"
					+ " spring-aspects.jar is not on the classpath;\n"
					+ "If you want to use auditing please add spring-aspects.jar to the classpath");
		}

		RootBeanDefinition def = new RootBeanDefinition();
		def.setBeanClassName(BEAN_CONFIGURER_ASPECT_CLASS_NAME);
		def.setFactoryMethodName("aspectOf");
		def.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);

		registry.registerBeanDefinition(BEAN_CONFIGURER_ASPECT_BEAN_NAME,
				new BeanComponentDefinition(def, BEAN_CONFIGURER_ASPECT_BEAN_NAME).getBeanDefinition());
	}
}
