/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.javabeans;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class PropertyValueSetToItselfInspectionTest extends LightInspectionTestCase {

  public void testSimple() {
    doTest("class Bean {\n" +
           "  private String x;\n" +
           "  public void setX(String x) {\n" +
           "    this.x = x;\n" +
           "  }\n" +
           "  public String getX() { return x; }\n" +
           "  void m(Bean b) {\n" +
           "    (b)./*Property value set to itself*/setX/**/(b.getX());\n" +
           "    this./*Property value set to itself*/setX/**/(getX());\n" +
           "  }\n" +
           "}");
  }

  public void testNoWarn() {
    doTest("class Bean {\n" +
           "  private String x;\n" +
           "  public void setX(String x) {\n" +
           "    this.x = x;\n" +
           "  }\n" +
           "  public String getX() { return x; }\n" +
           "  void m(Bean b, Bean c) {\n" +
           "    (b).setX(c.getX());\n" +
           "  }\n" +
           "}");
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new PropertyValueSetToItselfInspection();
  }
}