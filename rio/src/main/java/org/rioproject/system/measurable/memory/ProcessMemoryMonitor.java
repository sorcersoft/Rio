/*
 * Copyright 2008 to the original author or authors.
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
package org.rioproject.system.measurable.memory;

import org.rioproject.system.MeasuredResource;
import org.rioproject.system.measurable.MXBeanMonitor;
import org.rioproject.system.measurable.SigarHelper;
import org.rioproject.watch.ThresholdValues;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The <code>ProcessMemoryMonitor</code> object provides feedback information to the
 * <code>Memory</code> object, providing memory usage information for a process
 * obtained using JMX and SIGAR. SIGAR is used to obtain the process shared,
 * virtual and real memory size.
 *
 * <p>JMX is used to obtain detailed information on
 * heap and non-heap usage. If SIGAR is not available, only JMX will be used.
 *
 * <p><b>Note:</b>
 * <a href="http://www.hyperic.com/products/sigar.html">Hyperic SIGAR</a>
 * is licensed under the GPL with a FLOSS license exception, allowing it to be
 * included with the Rio Apache License v2 distribution. If for some reason the
 * GPL cannot be used with your distribution of Rio,
 * remove the <tt>RIO_HOME/lib/hyperic</tt> directory.
 *
 * @author Dennis Reedy
 */
public class ProcessMemoryMonitor implements MXBeanMonitor<MemoryMXBean> {
    private MemoryMXBean memBean;
    private String id;
    private ThresholdValues tVals;
    private SigarHelper sigar;
    private long pid;
    static Logger logger =
        Logger.getLogger(ProcessMemoryMonitor.class.getPackage().getName());
    private static double KB = 1024;
    private static double MB = Math.pow(KB, 2);

    public ProcessMemoryMonitor() {
        sigar = SigarHelper.getInstance();
        if (sigar!=null) {
            pid = sigar.getPid();
        }
        memBean = ManagementFactory.getMemoryMXBean();
    }

    public ProcessMemoryMonitor(int pid) {
        sigar = SigarHelper.getInstance();
        if (sigar!=null) {
            this.pid = pid;
        }
    }

    public void setPID(long pid) {
        this.pid = pid;
    }


    /* (non-Javadoc)
     * @see org.rioproject.system.measurable.MeasurableMonitor#terminate()
     */
    public void terminate() {
    }

    public void setID(String id) {
        this.id = id;
    }

    public void setThresholdValues(ThresholdValues tVals) {
        this.tVals = tVals;
    }

    public MeasuredResource getLastMeasuredResource() {
        return getMeasuredResource();
    }

    public MeasuredResource getMeasuredResource() {
        //if (memBean == null)
        //    memBean = ManagementFactory.getMemoryMXBean();
        ProcessMemoryUtilization memoryUtilization;
        MemoryUsage heapUsage = null;
        MemoryUsage nonHeapUsage = null;
        double utilization = 0;

        if(memBean!=null) {
            heapUsage = memBean.getHeapMemoryUsage();
            nonHeapUsage = memBean.getNonHeapMemoryUsage();
            utilization = (double)heapUsage.getUsed()/(double)heapUsage.getMax();
        }
        if (sigar!=null) {
            try {
                double vSize = sigar.getProcessVirtualMemorySize(pid)/MB;
                double resident = sigar.getProcessResidentMemory(pid);
                double shared = sigar.getProcessSharedMemory(pid);
                vSize = (vSize>0?vSize/MB:vSize);
                resident = (resident>0?resident/MB:resident);
                shared = (shared>0?shared/MB:shared);
                /*
                private NumberFormat nf = NumberFormat.getInstance();
                nf.setMaximumFractionDigits(2);
                System.out.println("VSize              = "+nf.format(vSize)+" MB");
                System.out.println("Resident           = "+nf.format(resident)+" MB");
                System.out.println("Shared             = "+shared);
                System.out.println("Heap Init          = "+nf.format(heapUsage.getInit()/MB)+" MB");
                System.out.println("Heap Used          = "+nf.format(heapUsage.getUsed()/MB)+" MB");
                System.out.println("Heap Max           = "+nf.format(heapUsage.getMax()/MB)+" MB");
                System.out.println("Heap Committed     = "+nf.format(heapUsage.getMax()/MB)+" MB");
                System.out.println("Non Heap Init      = "+nf.format(nonHeapUsage.getInit()/MB)+" MB");
                System.out.println("Non Heap Used      = "+nf.format(nonHeapUsage.getUsed()/MB)+" MB");
                System.out.println("Non Heap Max       = "+nf.format(nonHeapUsage.getMax()/MB)+" MB");
                System.out.println("Non Heap Committed = "+nf.format(nonHeapUsage.getMax()/MB)+" MB");
                System.out.println("**************");
                */
                if(memBean!=null) {
                    memoryUtilization =
                        new ProcessMemoryUtilization(id,
                                                     utilization,
                                                     vSize,
                                                     resident,
                                                     shared,
                                                     heapUsage.getInit()/MB,
                                                     heapUsage.getUsed()/MB,
                                                     heapUsage.getMax()/MB,
                                                     heapUsage.getCommitted()/MB,
                                                     nonHeapUsage.getInit()/MB,
                                                     nonHeapUsage.getUsed()/MB,
                                                     nonHeapUsage.getMax()/MB,
                                                     nonHeapUsage.getCommitted()/MB,
                                                     tVals);
                } else {
                    memoryUtilization =
                        new ProcessMemoryUtilization(id,
                                                     utilization,
                                                     vSize,
                                                     resident,
                                                     shared,
                                                     tVals);
                }

            } catch (Exception e) {
                logger.log(Level.WARNING,
                           "SIGAR exception getting Process Memory",
                           e);
                memoryUtilization = getJvmMemoryUtilization(utilization,
                                                            heapUsage,
                                                            nonHeapUsage);
            }

        } else {
            memoryUtilization = getJvmMemoryUtilization(utilization,
                                                        heapUsage,
                                                        nonHeapUsage);
        }
        return memoryUtilization;
    }

    public void setMXBean(MemoryMXBean mxBean) {
        this.memBean = mxBean;
    }

    public MemoryMXBean getMXBean() {
        return memBean;
    }

    private ProcessMemoryUtilization getJvmMemoryUtilization(double utilization,
                                                             MemoryUsage heapUsage,
                                                             MemoryUsage nonHeapUsage) {
        return new ProcessMemoryUtilization(id,
                                            utilization,
                                            getInit(heapUsage),
                                            getUsed(heapUsage),
                                            getMax(heapUsage),
                                            getCommitted(heapUsage),
                                            getInit(nonHeapUsage),
                                            getUsed(nonHeapUsage),
                                            getMax(nonHeapUsage),
                                            getCommitted(nonHeapUsage),
                                            tVals);
    }

    private double getInit(MemoryUsage mUsage) {
        return mUsage==null?-1:mUsage.getInit()/MB;
    }

    private double getUsed(MemoryUsage mUsage) {
        return mUsage==null?-1:mUsage.getUsed()/MB;
    }

    private double getMax(MemoryUsage mUsage) {
        return mUsage==null?-1:mUsage.getMax()/MB;
    }
    private double getCommitted(MemoryUsage mUsage) {
        return mUsage==null?-1:mUsage.getCommitted()/MB;
    }

}

