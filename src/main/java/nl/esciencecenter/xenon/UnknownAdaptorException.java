/**
 * Copyright 2013 Netherlands eScience Center
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
package nl.esciencecenter.xenon;

/**
 * Thrown when an unknown adaptor name is provided.  
 */
public class UnknownAdaptorException extends XenonException {

    private static final long serialVersionUID = 1L;

    public UnknownAdaptorException(String adaptorName, String message, Throwable t) {
        super(adaptorName, message, t);
    }

    public UnknownAdaptorException(String adaptorName, String message) {
        super(adaptorName, message);
    }
}
