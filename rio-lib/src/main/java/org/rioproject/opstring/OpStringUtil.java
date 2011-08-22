/*
 * Copyright to the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.rioproject.opstring;

import org.rioproject.util.PropertyHelper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utilities for working with OpStrings.
 *
 * @author Dennis Reedy
 */
public class OpStringUtil {
    static Logger logger = Logger.getLogger(OpStringUtil.class.getPackage().getName());

    /**
     * Check if the codebase is null or the codebase needs to be resolved
     *
     * @param opstring The OperationalString to check
     * @param codebase If the codebase is not set, set it to this value
     *
     * @throws java.io.IOException If the jars cannot be served
     */
    public static void checkCodebase(OperationalString opstring, String codebase) throws IOException {
        for (ServiceElement elem : opstring.getServices()) {
            checkCodebase(elem, codebase);
        }
        OperationalString[] nesteds = opstring.getNestedOperationalStrings();
        for (OperationalString nested : nesteds) {
            checkCodebase(nested, codebase);
        }
    }

    /**
     * Check if the codebase is null or the codebase needs to be resolved
     *
     * @param elem The ServiceElement to check
     * @param codebase If the codebase is not set, set it to this value
     *
     * @throws java.io.IOException If the jars cannot be served
     */
    public static void checkCodebase(ServiceElement elem, String codebase) throws IOException {
        if (codebase != null) {
            if (!codebase.endsWith("/"))
                codebase = codebase + "/";
        }
        ClassBundle bundle = elem.getComponentBundle();
        if (bundle.getCodebase() == null) {
            if (codebase == null) {
                if (logger.isLoggable(Level.WARNING))
                    logger.warning("Cannot fix null codebase for [" + elem.getName() + "], unknown codebase");
                return;
            }
            for(String jar : bundle.getJARNames())
                canServe(jar, codebase);
            bundle.setCodebase(codebase);
            logger.fine("Fixed ClassBundle "+bundle);

        } else if (bundle.getRawCodebase().startsWith("$[")) {
            String resolved = PropertyHelper.expandProperties(bundle.getRawCodebase(), PropertyHelper.RUNTIME);
            if (resolved == null) {
                if (logger.isLoggable(Level.FINE))
                    logger.fine("Cannot fix ["+bundle.getRawCodebase()+"] codebase for ["+elem.getName()+"], "+
                                "unknown property");
                return;
            }
            canServe(bundle.getClassName(), codebase);
            bundle.setCodebase(codebase);
        }

        ClassBundle[] exports = elem.getExportBundles();
        StringBuilder sb = new StringBuilder();

        StringBuilder sb1 = new StringBuilder();
        for (ClassBundle export : exports) {
            if (export.getCodebase() == null) {
                for(String jar : export.getJARNames()) {
                    canServe(jar, codebase);
                }
                export.setCodebase(codebase);
                logger.fine("Fixed export ClassBundle "+export);
            } else if (export.getRawCodebase().startsWith("$[")) {
                String resolved = PropertyHelper.expandProperties(export.getRawCodebase(), PropertyHelper.RUNTIME);
                for(String jar : export.getJARNames())
                    canServe(jar, resolved);
                export.setCodebase(resolved);
                logger.fine("Fixed export ClassBundle "+export);
            }
            for(String jar : export.getJARNames()) {
                if(sb1.length()>0)
                    sb1.append(", ");
                else
                    sb1.append("\n");
                sb1.append(export.getCodebase()).append(jar);
            }
        }
        sb.append(sb1.toString());
        if (logger.isLoggable(Level.INFO)) {
            logger.info(elem.getName()+" derived classpath for loading artifact "+sb.toString());
        }
    }

    private static void canServe(String name, String codebase) throws IOException {
        if(name.equals("rio-dl.jar") || name.equals("jsk-dl.jar"))
            return;
        InputStream is = null;
        URLConnection conn = null;
        try {
            URL url = new URL(codebase + name);
            if(codebase.startsWith("file:")) {
                try {
                    File f = new File(url.toURI());
                    if(!f.exists())
                        throw new IOException("Could not create URI from "+codebase);
                } catch (URISyntaxException e) {
                    throw new IOException("Could not create URI from "+codebase,
                                          e);
                }

            } else {
                conn = url.openConnection();
                is = conn.getInputStream();
                if(logger.isLoggable(Level.FINER))
                    logger.finer("Opened connection for "+url.toExternalForm());
            }

        } finally {
            if(is!=null)
                is.close();
            if(conn!=null && conn instanceof HttpURLConnection)
                ((HttpURLConnection)conn).disconnect();
        }
    }

}