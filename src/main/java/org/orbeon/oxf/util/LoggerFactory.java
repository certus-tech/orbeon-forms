/**
 * Copyright (C) 2009 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.DOMSerializer;
import org.orbeon.oxf.processor.Processor;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.properties.Properties;
import org.orbeon.oxf.properties.PropertySet;

/**
 * Logging initialization based on log4j 2. Legacy org.apache.log4j calls throughout the codebase
 * are routed to log4j 2 by the log4j-1.2-api bridge.
 */
public class LoggerFactory {

    public static final String LOG4J_DOM_CONFIG_PROPERTY = "oxf.log4j-config";

    public static final org.apache.log4j.Logger logger = LoggerFactory.createLogger(LoggerFactory.class);

    public static org.apache.log4j.Logger createLogger(String name) {
        return org.apache.log4j.Logger.getLogger(name);
    }

    public static org.apache.log4j.Logger createLogger(Class clazz) {
        return org.apache.log4j.Logger.getLogger(clazz.getName());
    }

    /*
     * Init basic config until resource manager is setup.
     */
    public static void initBasicLogger() {
        final ConfigurationBuilder<BuiltConfiguration> builder = ConfigurationBuilderFactory.newConfigurationBuilder();
        builder.setConfigurationName("OrbeonBasicLogger");
        builder.setStatusLevel(Level.ERROR);
        builder.add(
            builder.newAppender("ConsoleAppender", "Console")
                .addAttribute("target", ConsoleAppender.Target.SYSTEM_ERR)
                .add(
                    builder.newLayout("PatternLayout")
                        .addAttribute("pattern", PatternLayout.DEFAULT_CONVERSION_PATTERN)
                )
        );
        builder.add(
            builder.newRootLogger(Level.INFO)
                .add(builder.newAppenderRef("ConsoleAppender"))
        );
        Configurator.initialize(builder.build());
    }

    /**
     * Init logging. Needs Orbeon Forms Properties system up and running.
     */
    public static void initLogger() {
        try {
            // Accept both xs:string and xs:anyURI types

            final PropertySet propertySet = Properties.instance().getPropertySet();
            if (propertySet == null)
                throw new OXFException("Property set not found.");

            final String log4jConfigURL = propertySet.getStringOrURIAsString(LOG4J_DOM_CONFIG_PROPERTY, false);

            if (log4jConfigURL != null) {
                final Processor urlGenerator = PipelineUtils.createURLGenerator(log4jConfigURL, true);
                final DOMSerializer domSerializer = new DOMSerializer();
                PipelineUtils.connect(urlGenerator, ProcessorImpl.OUTPUT_DATA, domSerializer, ProcessorImpl.INPUT_DATA);
                // Candidate for Scala withPipelineContext
                final PipelineContext pipelineContext = new PipelineContext();
                boolean success = false;
                final org.w3c.dom.Element element;
                try {
                    urlGenerator.reset(pipelineContext);
                    domSerializer.reset(pipelineContext);
                    element = domSerializer.runGetW3CDocument(pipelineContext).getDocumentElement();
                    success = true;
                } finally {
                    pipelineContext.destroy(success);
                }
                configureFromElement(element);
            } else {
                logger.info("Property " + LOG4J_DOM_CONFIG_PROPERTY + " not set. Skipping logging initialization.");
            }
        } catch (Throwable e) {
            logger.error("Cannot load Log4J configuration. Skipping logging initialization", e);
        }
    }

    /**
     * Configure log4j 2 from the given configuration document, loaded through the resource manager.
     */
    private static void configureFromElement(org.w3c.dom.Element element) throws Exception {
        final Transformer transformer = TransformerFactory.newInstance().newTransformer();
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(element), new StreamResult(outputStream));

        final ConfigurationSource source =
            new ConfigurationSource(new ByteArrayInputStream(outputStream.toByteArray()));

        final LoggerContext loggerContext = Configurator.initialize(LoggerFactory.class.getClassLoader(), source);
        loggerContext.start();
    }
}
