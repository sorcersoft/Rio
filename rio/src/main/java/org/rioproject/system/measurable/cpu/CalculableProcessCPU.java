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
package org.rioproject.system.measurable.cpu;

import org.rioproject.watch.Calculable;

/**
 * A Calculable used to collect CPU system utilization
 *
 * @author Dennis Reedy
 */
public class CalculableProcessCPU extends Calculable {
    static final long serialVersionUID = 1L;
    private ProcessCpuUtilization cpuUtilization;

    /**
     * Creates new CalculableCPU
     *
     * @param id the identifier for this Calculable record
     * @param value utilization to record
     * @param when the time when the recorded value was captured
     */
    public CalculableProcessCPU(String id, double value, long when) {
        super(id, value, when);
    }

    /**
     * Creates new CalculableCPU
     *
     * @param id the identifier for this Calculable record
     * @param cpuUtilization holds CPU utilization
     * @param when the time when the recorded value was captured
     */
    public CalculableProcessCPU(String id,
                                ProcessCpuUtilization cpuUtilization,
                                long when) {
        super(id, cpuUtilization.getTotalPercentage(), when);
        this.cpuUtilization = cpuUtilization;
    }

    /**
     * Get the cpu kernel usage
     *
     * @return The cpu kernel use as a percentage
     */
    public double getSystem() {
        return (cpuUtilization ==null? Double.NaN: cpuUtilization.getSystem());
    }

    /**
     * Get the cpu user usage
     *
     * @return The cpu user use as a percentage
     */
    public double getUser() {
        return (cpuUtilization ==null? Double.NaN: cpuUtilization.getUser());
    }

    /**
     * Get the cpu utilization
     *
     * @return The cpu utilization
     */
    public double getTotal() {
        return (cpuUtilization ==null? Double.NaN: cpuUtilization.getTotal());
    }

    /**
     * Get the ProcessCpuUtilization
     *
     * @return The ProcessCpuUtilization.
     */
    public ProcessCpuUtilization getProcessCpuUtilization() {
        return cpuUtilization;
    }

    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("CalculableProcessCPU { ");
        sb.append("system=").append(getSystem());
        sb.append(", user=").append(getUser());
        sb.append(", total=").append(getTotal());
        sb.append(", value=").append(getValue());
        sb.append("}");
        return sb.toString();
    }

    /**
     * Gets an archival representation for this Calculable
     *
     * @return a string representation in archive format
     */
    public String getArchiveRecord() {
        return(getId() +'|'+
               getSystem() +'|'+
               getUser() +'|'+
               getValue() +'|'+
               getWhen());
    }
}
