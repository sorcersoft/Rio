/*
 * Copyright 2008 the original author or authors.
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
package org.rioproject.examples.hospital;

import java.io.IOException;
import java.util.List;

public interface Doctor {
    enum Status {ON_CALL, ON_DUTY, OFF_DUTY}

    Status getStatus() throws IOException;
    void onCall() throws IOException;
    void onDuty() throws IOException;
    void offDuty() throws IOException;
    String getName() throws IOException;
    String getSpecialty() throws IOException;
    void assignPatient(Patient p) throws IOException;
    void removePatient(Patient p) throws IOException;
    List<Patient> getPatients() throws IOException;
    void updatePatient(Patient p) throws IOException;
}