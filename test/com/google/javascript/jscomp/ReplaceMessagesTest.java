/*
 * Copyright 2008 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.javascript.jscomp;

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.jscomp.JsMessage.Style.CLOSURE;
import static com.google.javascript.jscomp.JsMessageVisitor.MESSAGE_NOT_INITIALIZED_CORRECTLY;
import static com.google.javascript.jscomp.JsMessageVisitor.MESSAGE_TREE_MALFORMED;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Node.SideEffectFlags;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test which checks that replacer works correctly. */
@RunWith(JUnit4.class)
public final class ReplaceMessagesTest extends CompilerTestCase {

  /** Indicates which part of the replacement we're currently testing */
  enum TestMode {
    // Test full replacement from `goog.getMsg()` to final message values.
    FULL_REPLACE,
    // Test replacement of `goog.getMsg()` with the protected function call form.
    // e.g.
    // ```javascript
    // var MSG_G =
    //     __jscomp_define_msg__(
    //         {
    //           "alt_id": null,
    //           "key":    "MSG_G",
    //           "msg_text": "${$amount}"
    //         },
    //         {amount:x + ""});`
    // ```
    PROTECT_MSGS,
    // Test replacement of the protected function call form with the final message values.
    REPLACE_PROTECTED_MSGS
  }

  // Messages returned from fake bundle, keyed by `JsMessage.id`.
  private Map<String, JsMessage> messages;
  private boolean strictReplacement;
  private TestMode testMode = TestMode.FULL_REPLACE;

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    final ReplaceMessages replaceMessages =
        new ReplaceMessages(compiler, new SimpleMessageBundle(), CLOSURE, strictReplacement);
    switch (testMode) {
      case FULL_REPLACE:
        return replaceMessages.getFullReplacementPass();
      case PROTECT_MSGS:
        return replaceMessages.getMsgProtectionPass();
      case REPLACE_PROTECTED_MSGS:
        return replaceMessages.getReplacementCompletionPass();
    }
    throw new UnsupportedOperationException("unexpected testMode: " + testMode);
  }

  /**
   * The primary test method to use in this file.
   *
   * @param originalJs The original, input JS code
   * @param protectedJs What the code should look like after message definitions are is protected
   *     from mangling during optimizations, but before they are replaced with the localized message
   *     data.
   * @param expectedJs What the code should look like after full replacement with localized messages
   *     has been done.
   */
  private void multiPhaseTest(String originalJs, String protectedJs, String expectedJs) {
    // The PROTECT_MSGS mode needs to add externs for the protection functions.
    allowExternsChanges();
    testMode = TestMode.FULL_REPLACE;
    test(originalJs, expectedJs);
    testMode = TestMode.PROTECT_MSGS;
    test(originalJs, protectedJs);
    testMode = TestMode.REPLACE_PROTECTED_MSGS;
    test(protectedJs, expectedJs);
  }

  /**
   * Test for warnings that apply to both the full replace and the initial protection of messages.
   *
   * @param originalJs The original, input JS code
   * @param protectedJs What the code should look like after message definitions are is protected
   *     from mangling during optimizations, but before they are replaced with the localized message
   *     data.
   * @param expectedJs What the code should look like after full replacement with localized messages
   * @param diagnosticType expected warning
   */
  private void multiPhaseTestWarning(
      String originalJs, String protectedJs, String expectedJs, DiagnosticType diagnosticType) {
    // The PROTECT_MSGS mode needs to add externs for the protection functions.
    allowExternsChanges();
    testMode = TestMode.FULL_REPLACE;
    testWarning(originalJs, diagnosticType);
    testMode = TestMode.PROTECT_MSGS;
    testWarning(originalJs, diagnosticType);
    testMode = TestMode.REPLACE_PROTECTED_MSGS;
    test(protectedJs, expectedJs);
  }

  /**
   * Test for errors that are detected before attempting to look up the messages in the bundle.
   *
   * @param originalJs The original, input JS code
   * @param diagnosticType expected error
   */
  private void multiPhaseTestPreLookupError(String originalJs, DiagnosticType diagnosticType) {
    // The PROTECT_MSGS mode needs to add externs for the protection functions.
    allowExternsChanges();
    testMode = TestMode.FULL_REPLACE;
    testError(originalJs, diagnosticType);
    testMode = TestMode.PROTECT_MSGS;
    testError(originalJs, diagnosticType);
  }

  /**
   * Test for errors that are detected after attempting to look up the messages in the bundle.
   *
   * @param originalJs The original, input JS code
   * @param protectedJs What the code should look like after message definitions are is protected
   *     from mangling during optimizations, but before they are replaced with the localized message
   *     data.
   * @param diagnosticType expected error
   */
  private void multiPhaseTestPostLookupError(
      String originalJs, String protectedJs, DiagnosticType diagnosticType) {
    // The PROTECT_MSGS mode needs to add externs for the protection functions.
    allowExternsChanges();
    testMode = TestMode.FULL_REPLACE;
    testError(originalJs, diagnosticType);
    testMode = TestMode.PROTECT_MSGS;
    test(originalJs, protectedJs);
    testMode = TestMode.REPLACE_PROTECTED_MSGS;
    testError(protectedJs, diagnosticType);
  }

  /**
   * Test for errors that are detected before attempting to look up the messages in the bundle.
   *
   * @param originalJs The original, input JS code
   * @param protectedJs What the code should look like after message definitions are is protected
   *     from mangling during optimizations, but before they are replaced with the localized message
   *     data.
   * @param diagnosticType expected error
   * @param description text expected to be in the error message
   */
  private void multiPhaseTestPostLookupError(
      String originalJs, String protectedJs, DiagnosticType diagnosticType, String description) {
    // The PROTECT_MSGS mode needs to add externs for the protection functions.
    allowExternsChanges();
    testMode = TestMode.FULL_REPLACE;
    testError(originalJs, diagnosticType, description);
    testMode = TestMode.PROTECT_MSGS;
    test(originalJs, protectedJs);
    testMode = TestMode.REPLACE_PROTECTED_MSGS;
    testError(protectedJs, diagnosticType, description);
  }

  @Override
  protected int getNumRepetitions() {
    // No longer valid on the second run.
    return 1;
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    messages = new HashMap<>();
    strictReplacement = false;
    enableTypeCheck();
    replaceTypesWithColors();
    enableTypeInfoValidation();
    ignoreWarnings(DiagnosticGroups.MISSING_PROPERTIES);
  }

  @Test
  public void testProtectedMessagesAndFallbackAreRemovable() {
    // Externs for the protection functions will be added.
    allowExternsChanges();
    testMode = TestMode.PROTECT_MSGS;
    test(
        lines(
            "/** @desc d */", //
            "var MSG_A = goog.getMsg('asdf');",
            "/** @desc d */",
            "var MSG_B = goog.getMsg('qwerty');",
            "var x = goog.getMsgWithFallback(MSG_A, MSG_B);",
            ""),
        lines(
            "/** @desc d */",
            "var MSG_A =",
            "    __jscomp_define_msg__(",
            "        {",
            "          \"key\":    \"MSG_A\",",
            "          \"msg_text\":\"asdf\",",
            "        });",
            "/** @desc d */",
            "var MSG_B =",
            "    __jscomp_define_msg__(",
            "        {",
            "          \"key\":    \"MSG_B\",",
            "          \"msg_text\":\"qwerty\",",
            "      });",
            "var x = __jscomp_msg_fallback__(\"MSG_A\", MSG_A, \"MSG_B\", MSG_B);",
            ""));
    // It's important that all of the protective function calls be marked as having no side effects,
    // so they will be removed during optimizations if they are unused.
    final Node jsRoot = getLastCompiler().getJsRoot();
    final Node script = jsRoot.getFirstChild();
    final Node msgAVarNode = script.getFirstChild();

    final Node msgANameNode = msgAVarNode.getOnlyChild();
    assertNode(msgANameNode).isName("MSG_A");
    final Node msgACallNode = msgANameNode.getOnlyChild();
    assertThat(msgACallNode.getSideEffectFlags()).isEqualTo(SideEffectFlags.NO_SIDE_EFFECTS);

    final Node msgBVarNode = msgAVarNode.getNext();
    final Node msgBNameNode = msgBVarNode.getOnlyChild();
    assertNode(msgBNameNode).isName("MSG_B");
    final Node msgBCallNode = msgANameNode.getOnlyChild();
    assertThat(msgBCallNode.getSideEffectFlags()).isEqualTo(SideEffectFlags.NO_SIDE_EFFECTS);

    final Node xVarNode = msgBVarNode.getNext();
    final Node xNameNode = xVarNode.getOnlyChild();
    assertNode(xNameNode).isName("x");
    final Node xCallNode = xNameNode.getOnlyChild();
    assertThat(xCallNode.getSideEffectFlags()).isEqualTo(SideEffectFlags.NO_SIDE_EFFECTS);
  }

  @Test
  public void testReplaceSimpleMessage() {
    registerMessage(getTestMessageBuilder("MSG_A").appendStringPart("Hi\nthere").build());

    multiPhaseTest(
        lines(
            "/** @desc d */", //
            "var MSG_A = goog.getMsg('asdf');"),
        lines(
            "/**",
            " * @desc d",
            " */",
            "var MSG_A =",
            "    __jscomp_define_msg__(",
            "        {",
            "          \"key\":    \"MSG_A\",",
            "          \"msg_text\":\"asdf\",",
            "        });"),
        lines(
            "/** @desc d */", //
            "var MSG_A='Hi\\nthere'"));
  }

  @Test
  public void testReplaceExternalMessage() {
    registerMessage(getTestMessageBuilder("12345").appendStringPart("Saluton!").build());

    multiPhaseTest(
        lines(
            "/** @desc d */", //
            "var MSG_EXTERNAL_12345 = goog.getMsg('Hello!');"),
        lines(
            "/**",
            " * @desc d",
            " */",
            "var MSG_EXTERNAL_12345 =",
            "    __jscomp_define_msg__(",
            "        {",
            "          \"key\":    \"MSG_EXTERNAL_12345\",",
            "          \"msg_text\":\"Hello!\",",
            "        });"),
        lines(
            "/** @desc d */", //
            "var MSG_EXTERNAL_12345='Saluton!'"));
  }

  @Test
  public void testReplaceSimpleMessageDefinedWithAdd() {
    registerMessage(getTestMessageBuilder("MSG_A").appendStringPart("Hi\nthere").build());

    multiPhaseTest(
        lines(
            "/** @desc d */", //
            "var MSG_A = goog.getMsg('abcd' + 'efgh');"),
        lines(
            "/**",
            " * @desc d",
            " */",
            "var MSG_A =",
            "    __jscomp_define_msg__(",
            "        {",
            "          \"key\":\"MSG_A\",",
            "          \"msg_text\":\"abcdefgh\",",
            "        });"),
        lines(
            "/** @desc d */", //
            "var MSG_A='Hi\\nthere'"));
  }

  @Test
  public void testMissingAlternateMessage() {
    multiPhaseTest(
        lines(
            "/**", //
            " * @desc d",
            " * @alternateMessageId 1984",
            " */",
            "var MSG_A = goog.getMsg('asdf');"),
        lines(
            "/**",
            " * @desc d",
            " * @alternateMessageId 1984",
            " */",
            "var MSG_A =",
            "    __jscomp_define_msg__(",
            "        {",
            "          \"key\":\"MSG_A\",",
            "          \"alt_id\":\"1984\",",
            "          \"msg_text\":\"asdf\",",
            "        });"),
        lines(
            "/**", //
            " * @desc d",
            " * @alternateMessageId 1984",
            " */",
            "var MSG_A='asdf'"));
  }

  @Test
  public void testAlternateMessageWithMismatchedParts() {
    registerMessage(
        getTestMessageBuilder("1984")
            .setDesc("B desc")
            .setMeaning("B meaning")
            .appendStringPart("Hello!")
            .appendStringPart(" Welcome!")
            .build());

    multiPhaseTest(
        lines(
            "/**",
            " * @desc d",
            " * @alternateMessageId 1984",
            " */",
            "var MSG_A = goog.getMsg('asdf');"),
        lines(
            "/**",
            " * @desc d",
            " * @alternateMessageId 1984",
            " */",
            "var MSG_A =",
            "    __jscomp_define_msg__(",
            "        {",
            "          \"key\":\"MSG_A\",",
            "          \"alt_id\":\"1984\",",
            "          \"msg_text\":\"asdf\",",
            "        });"),
        lines(
            "/**", //
            " * @desc d",
            " * @alternateMessageId 1984",
            " */",
            "var MSG_A = 'Hello! Welcome!';"));
  }

  /**
   * Returns a message builder that will use the same string as both the key and ID of the message.
   */
  private JsMessage.Builder getTestMessageBuilder(String keyAndId) {
    return new JsMessage.Builder().setKey(keyAndId).setId(keyAndId);
  }

  @Test
  public void testAlternateMessageWithMismatchedPlaceholders() {
    registerMessage(
        getTestMessageBuilder("1984")
            .setDesc("B desc")
            .setMeaning("B meaning")
            .appendStringPart("Hello, ")
            .appendPlaceholderReference("first_name")
            .appendStringPart("!")
            .build());

    multiPhaseTestPostLookupError(
        lines(
            "/**",
            " * @desc B desc",
            " * @meaning B meaning",
            " * @alternateMessageId 1984",
            " */",
            "var MSG_A = goog.getMsg('Hello, {$name}!', {name: name});"),
        lines(
            "/**", //
            " * @desc B desc",
            " * @meaning B meaning",
            " * @alternateMessageId 1984",
            " */",
            "var MSG_A =",
            "    __jscomp_define_msg__(",
            "        {",
            "          \"key\":    \"MSG_A\",",
            "          \"alt_id\": \"1984\",",
            "          \"meaning\":\"B meaning\",",
            "          \"msg_text\":\"Hello, {$name}!\"",
            "        },",
            "        {'name': name});",
            ""),
        ReplaceMessages.INVALID_ALTERNATE_MESSAGE_PLACEHOLDERS);
  }

  @Test
  public void testReplaceValidAlternateMessage() {
    registerMessage(getTestMessageBuilder("1984").appendStringPart("Howdy\npardner").build());

    multiPhaseTest(
        lines(
            "/**",
            " * @desc B desc",
            " * @alternateMessageId 1984",
            " */",
            "var MSG_A = goog.getMsg('asdf');"),
        lines(
            "/**",
            " * @desc B desc",
            " * @alternateMessageId 1984",
            " */",
            "var MSG_A =",
            "    __jscomp_define_msg__(",
            "        {",
            "          \"key\":\"MSG_A\",",
            "          \"alt_id\":\"1984\",",
            "          \"msg_text\":\"asdf\",",
            "        });\n"),
        lines(
            "/**",
            " * @desc B desc",
            " * @alternateMessageId 1984",
            " */",
            "var MSG_A='Howdy\\npardner'"));
  }

  @Test
  public void testIgnoreUnnecessaryAlternateMessage() {
    registerMessage(getTestMessageBuilder("1984").appendStringPart("Howdy\npardner").build());
    registerMessage(
        getTestMessageBuilder("MSG_A")
            .setDesc("Greeting.")
            .setAlternateId("1984")
            .appendStringPart("Hi\nthere")
            .build());

    multiPhaseTest(
        lines(
            "/**", //
            " * @desc d",
            " * @alternateMessageId 1984",
            " */",
            "var MSG_A = goog.getMsg('asdf');"),
        lines(
            "/**",
            " * @desc d",
            " * @alternateMessageId 1984",
            " */",
            "var MSG_A =",
            "    __jscomp_define_msg__(",
            "        {",
            "          \"key\":\"MSG_A\",",
            "          \"alt_id\":\"1984\",",
            "          \"msg_text\":\"asdf\",",
            "        });",
            " "),
        lines(
            "/**", //
            " * @desc d",
            " * @alternateMessageId 1984",
            " */",
            "var MSG_A = 'Hi\\nthere';"));
  }

  @Test
  public void testAlternateTrumpsFallback() {
    registerMessage(getTestMessageBuilder("1984").appendStringPart("Howdy\npardner").build());

    registerMessage(getTestMessageBuilder("MSG_B").appendStringPart("Good\nmorrow, sir").build());

    multiPhaseTest(
        lines(
            "/**",
            " * @desc d",
            " * @alternateMessageId 1984",
            "*/",
            "var MSG_A = goog.getMsg('asdf');",
            "/**",
            " * @desc d",
            "*/",
            "var MSG_B = goog.getMsg('ghjk');",
            "var x = goog.getMsgWithFallback(MSG_A, MSG_B);"),
        lines(
            "/**",
            " * @desc d",
            " * @alternateMessageId 1984",
            " */",
            "var MSG_A =",
            "    __jscomp_define_msg__(",
            "        {",
            "          \"key\":\"MSG_A\",",
            "          \"alt_id\":\"1984\",",
            "          \"msg_text\":\"asdf\",",
            "        });",
            "/**",
            " * @desc d",
            " */",
            "var MSG_B =",
            "    __jscomp_define_msg__(",
            "        {",
            "          \"key\":\"MSG_B\",",
            "          \"msg_text\":\"ghjk\",",
            "        });",
            "var x = __jscomp_msg_fallback__(\"MSG_A\", MSG_A, \"MSG_B\", MSG_B);"),
        lines(
            "/**",
            "    @desc d",
            "    @alternateMessageId 1984",
            "*/",
            "var MSG_A = 'Howdy\\npardner';",
            "/**",
            "    @desc d",
            "*/",
            "var MSG_B = 'Good\\nmorrow, sir';",
            "var x = MSG_A;"));
  }

  @Test
  public void testFallbackWithAlternate() {
    registerMessage(getTestMessageBuilder("1984").appendStringPart("Howdy\npardner").build());

    multiPhaseTest(
        lines(
            "/**",
            "    @desc d",
            "*/",
            "var MSG_A = goog.getMsg('asdf');",
            "/**",
            "    @desc d",
            "    @alternateMessageId 1984",
            "*/",
            "var MSG_B = goog.getMsg('ghjk');",
            "var x = goog.getMsgWithFallback(MSG_A, MSG_B);"),
        lines(
            "/**",
            " * @desc d",
            " */",
            "var MSG_A =",
            "    __jscomp_define_msg__(",
            "        {",
            "          \"key\":\"MSG_A\",",
            "          \"msg_text\":\"asdf\",",
            "        });",
            "/**",
            " * @desc d",
            "    @alternateMessageId 1984     */",
            "var MSG_B =",
            "    __jscomp_define_msg__(",
            "        {",
            "          \"key\":\"MSG_B\",",
            "          \"alt_id\":\"1984\",",
            "          \"msg_text\":\"ghjk\",",
            "        });",
            "var x = __jscomp_msg_fallback__(\"MSG_A\", MSG_A, \"MSG_B\", MSG_B);",
            ""),
        lines(
            "/**",
            "    @desc d",
            "*/",
            "var MSG_A = 'asdf';",
            "/**",
            "    @desc d",
            "    @alternateMessageId 1984",
            "*/",
            "var MSG_B = 'Howdy\\npardner';",
            "var x = MSG_B;"));
  }

  @Test
  public void testNameReplacement() {
    registerMessage(
        getTestMessageBuilder("MSG_B")
            .appendStringPart("One ")
            .appendPlaceholderReference("measly")
            .appendStringPart(" ph")
            .build());

    multiPhaseTest(
        lines("/** @desc d */", "var MSG_B=goog.getMsg('asdf {$measly}', {measly: x});"),
        lines(
            "/**",
            " * @desc d",
            " */",
            "var MSG_B =",
            "    __jscomp_define_msg__(",
            "        {",
            "          \"key\":\"MSG_B\",",
            "          \"msg_text\":\"asdf {$measly}\",",
            "        }, {'measly': x});"),
        lines(
            "/** @desc d */", //
            "var MSG_B = 'One ' + x + ' ph';"));
  }

  @Test
  public void testNameReplacementWithFullOptionsBag() {
    registerMessage(
        getTestMessageBuilder("MSG_B")
            .appendStringPart("One ")
            .appendPlaceholderReference("measly")
            .appendStringPart(" ph")
            .build());

    multiPhaseTest(
        lines(
            "/** @desc d */",
            "var MSG_B =",
            "    goog.getMsg(",
            "        'asdf {$measly}',",
            "        {measly: x},",
            "        {",
            // use all allowed options
            "          html: true,",
            "          unescapeHtmlEntities: true,",
            // original_code and example get dropped, because they're only used
            // when generating the XMB file.
            "          original_code: {",
            "            'measly': 'getMeasley()'",
            "          },",
            "          example: {",
            "            'measly': 'very little'",
            "          },",
            "        });"),
        lines(
            "/**",
            " * @desc d",
            " */",
            "var MSG_B =",
            "    __jscomp_define_msg__(",
            "        {",
            "          \"key\":\"MSG_B\",",
            "          \"msg_text\":\"asdf {$measly}\",",
            "          \"escapeLessThan\":\"\",",
            "          \"unescapeHtmlEntities\":\"\"",
            "        },",
            "        {'measly': x});"),
        lines(
            "/** @desc d */", //
            "var MSG_B = 'One ' + x + ' ph';"));
  }

  @Test
  public void testGetPropReplacement() {
    registerMessage(getTestMessageBuilder("MSG_C").appendPlaceholderReference("amount").build());

    multiPhaseTest(
        lines(
            "/** @desc d */", //
            "var MSG_C = goog.getMsg('${$amount}', {amount: a.b.amount});"),
        lines(
            "/**",
            " * @desc d",
            " */",
            "var MSG_C =",
            "    __jscomp_define_msg__(",
            "        {",
            "          \"key\":\"MSG_C\",",
            "          \"msg_text\":\"${$amount}\",",
            "        }, {'amount': a.b.amount});",
            "     "),
        lines(
            "/** @desc d */", //
            "var MSG_C=a.b.amount"));
  }

  @Test
  public void testFunctionCallReplacement() {
    registerMessage(getTestMessageBuilder("MSG_D").appendPlaceholderReference("amount").build());

    multiPhaseTest(
        lines("/** @desc d */", "var MSG_D = goog.getMsg('${$amount}', {amount: getAmt()});"),
        lines(
            "/**",
            " * @desc d",
            " */",
            "var MSG_D =",
            "    __jscomp_define_msg__(",
            "        {",
            "          \"key\":\"MSG_D\",",
            "          \"msg_text\":\"${$amount}\",",
            "        }, {'amount': getAmt()});",
            "     "),
        lines(
            "/** @desc d */", //
            "var MSG_D=getAmt()"));
  }

  @Test
  public void testMethodCallReplacement() {
    registerMessage(getTestMessageBuilder("MSG_E").appendPlaceholderReference("amount").build());

    multiPhaseTest(
        lines(
            "/** @desc d */", //
            "var MSG_E = goog.getMsg('${$amount}', {amount: obj.getAmt()});"),
        lines(
            "/**",
            " * @desc d",
            " */",
            "var MSG_E =",
            "    __jscomp_define_msg__(",
            "        {",
            "          \"key\":\"MSG_E\",",
            "          \"msg_text\":\"${$amount}\",",
            "        }, {'amount': obj.getAmt()});"),
        lines(
            "/** @desc d */", //
            "var MSG_E=obj.getAmt()"));
  }

  @Test
  public void testMethodCallReplacementEmptyMessage() {
    registerMessage(getTestMessageBuilder("MSG_M").build());

    multiPhaseTest(
        lines(
            "/** @desc d */", //
            "var MSG_M = goog.getMsg('${$amount}', {amount: obj.getAmt()});"),
        lines(
            "/**",
            " * @desc d",
            " */",
            "var MSG_M =",
            "    __jscomp_define_msg__(",
            "        {",
            "          \"key\":\"MSG_M\",",
            "          \"msg_text\":\"${$amount}\",",
            "        }, {'amount': obj.getAmt()});\n"),
        "/** @desc d */\n var MSG_M=''");
  }

  @Test
  public void testHookReplacement() {
    registerMessage(
        getTestMessageBuilder("MSG_F")
            .appendStringPart("#")
            .appendPlaceholderReference("amount")
            .appendStringPart(".")
            .build());

    multiPhaseTest(
        "/** @desc d */\n var MSG_F = goog.getMsg('${$amount}', {amount: (a ? b : c)});",
        lines(
            "/**",
            " * @desc d",
            " */",
            "var MSG_F =",
            "    __jscomp_define_msg__(",
            "        {",
            "          \"key\":\"MSG_F\",",
            "          \"msg_text\":\"${$amount}\",",
            "        }, {'amount': a ? b : c});"),
        lines(
            "/** @desc d */", //
            "var MSG_F = '#' + (a?b:c) + '.';",
            ""));
  }

  @Test
  public void testAddReplacement() {
    registerMessage(getTestMessageBuilder("MSG_G").appendPlaceholderReference("amount").build());

    multiPhaseTest(
        lines(
            "/** @desc d */", //
            "var MSG_G = goog.getMsg('${$amount}', {amount: x + ''});"),
        lines(
            "/** @desc d */", //
            "var MSG_G =",
            "    __jscomp_define_msg__(",
            "        {",
            "          'key':'MSG_G',",
            "          \"msg_text\":\"${$amount}\",",
            "        },",
            "        {'amount': x + ''});"),
        lines(
            "/** @desc d */", //
            "var MSG_G=x+''"));
  }

  @Test
  public void testPlaceholderValueReferencedTwice() {
    registerMessage(
        getTestMessageBuilder("MSG_H")
            .appendPlaceholderReference("dick")
            .appendStringPart(", ")
            .appendPlaceholderReference("dick")
            .appendStringPart(" and ")
            .appendPlaceholderReference("jane")
            .build());

    multiPhaseTest(
        "/** @desc d */\n var MSG_H = goog.getMsg('{$dick}{$jane}', {jane: x, dick: y});",
        lines(
            "/**",
            " * @desc d",
            " */",
            "var MSG_H =",
            "    __jscomp_define_msg__(",
            "        {",
            "          \"key\":\"MSG_H\",",
            "          \"msg_text\":\"{$dick}{$jane}\",",
            "        }, {'jane': x, 'dick': y});",
            ""),
        lines(
            "/** @desc d */", //
            "var MSG_H = y + ', ' + y + ' and ' + x;"));
  }

  @Test
  public void testPlaceholderNameInLowerCamelCase() {
    registerMessage(
        getTestMessageBuilder("MSG_I")
            .appendStringPart("Sum: $")
            .appendPlaceholderReference("amtEarned")
            .build());

    multiPhaseTest(
        lines(
            "/** @desc d */", //
            "var MSG_I = goog.getMsg('${$amtEarned}', {amtEarned: x});"),
        lines(
            "/**",
            " * @desc d",
            " */",
            "var MSG_I =",
            "    __jscomp_define_msg__(",
            "        {",
            "          \"key\":\"MSG_I\",",
            "          \"msg_text\":\"${$amtEarned}\",",
            "        }, {'amtEarned': x});",
            "     "),
        lines(
            "/** @desc d */", //
            "var MSG_I='Sum: $'+x"));
  }

  @Test
  public void testQualifiedMessageName() {
    registerMessage(
        getTestMessageBuilder("MSG_J")
            .appendStringPart("One ")
            .appendPlaceholderReference("measly")
            .appendStringPart(" ph")
            .build());

    multiPhaseTest(
        lines(
            "/** @desc d */", //
            "a.b.c.MSG_J = goog.getMsg('asdf {$measly}', {measly: x});"),
        lines(
            "/**",
            " * @desc d",
            " */",
            "    a.b.c.MSG_J =",
            "    __jscomp_define_msg__(",
            "        {",
            "          \"key\":   'MSG_J',",
            "          \"msg_text\":\"asdf {$measly}\",",
            "        },",
            "        {'measly': x});",
            ""),
        lines(
            "/** @desc d */", //
            "a.b.c.MSG_J = 'One ' + x + ' ph';"));
  }

  @Test
  public void testPlaceholderInPlaceholderValue() {
    registerMessage(
        getTestMessageBuilder("MSG_L")
            .appendPlaceholderReference("a")
            .appendStringPart(" has ")
            .appendPlaceholderReference("b")
            .build());

    multiPhaseTest(
        lines(
            "/** @desc d */", //
            "var MSG_L = goog.getMsg('{$a} has {$b}', {a: '{$b}', b: 1});"),
        lines(
            "/**",
            " * @desc d",
            " */",
            "var MSG_L =",
            "    __jscomp_define_msg__(",
            "        {",
            "          \"key\":\"MSG_L\",",
            "          \"msg_text\":\"{$a} has {$b}\"",
            "        }, {'a': \"{$b}\", 'b': 1});",
            ""),
        lines(
            "/** @desc d */", //
            "var MSG_L = '{$b}' + ' has ' + 1;"));
  }

  @Test
  public void testSimpleMessageReplacementMissing() {
    multiPhaseTestWarning(
        lines(
            "/** @desc d */", //
            "var MSG_E = 'd*6a0@z>t';"), //
        lines(
            "/** @desc d */", //
            "var MSG_E =",
            "    __jscomp_define_msg__({\"key\":\"MSG_E\", \"msg_text\":\"d*6a0@z\\x3et\"});"),
        lines(
            "/** @desc d */", //
            "var MSG_E = 'd*6a0@z>t'"),
        MESSAGE_NOT_INITIALIZED_CORRECTLY);
  }

  @Test
  public void testSimpleMessageReplacementMissingWithNewStyle() {
    multiPhaseTest(
        "/** @desc d */\n var MSG_E = goog.getMsg('missing');",
        lines(
            "/**",
            " * @desc d",
            " */",
            "var MSG_E =",
            "    __jscomp_define_msg__(",
            "        {",
            "          \"key\":\"MSG_E\",",
            "          \"msg_text\":\"missing\",",
            "        });\n"),
        "/** @desc d */\n var MSG_E = 'missing'");
  }

  @Test
  public void testStrictModeAndMessageReplacementAbsentInBundle() {
    strictReplacement = true;
    multiPhaseTestPostLookupError(
        lines(
            "/** @desc d */", //
            "var MSG_E = goog.getMsg('Hello');",
            ""),
        lines(
            "/** @desc d */", //
            "var MSG_E = __jscomp_define_msg__({\"key\":\"MSG_E\", \"msg_text\":\"Hello\"});",
            ""),
        ReplaceMessages.BUNDLE_DOES_NOT_HAVE_THE_MESSAGE);
  }

  @Test
  public void testStrictModeAndMessageReplacementAbsentInNonEmptyBundle() {
    registerMessage(
        getTestMessageBuilder("MSG_J")
            .appendStringPart("One ")
            .appendPlaceholderReference("measly")
            .appendStringPart(" ph")
            .build());

    strictReplacement = true;
    multiPhaseTestPostLookupError(
        lines(
            "/** @desc d */", //
            "var MSG_E = goog.getMsg('Hello');"),
        lines(
            "/** @desc d */", //
            "var MSG_E = __jscomp_define_msg__({\"key\":\"MSG_E\", \"msg_text\":\"Hello\"});"),
        ReplaceMessages.BUNDLE_DOES_NOT_HAVE_THE_MESSAGE);
  }

  @Test
  public void testFunctionReplacementMissing() {
    multiPhaseTestWarning(
        "var MSG_F = function() {return 'asdf'};", //
        lines(
            "var MSG_F = function() {", //
            "  return __jscomp_define_msg__(",
            "      {",
            "        \"key\":\"MSG_F\",",
            "        \"msg_text\":\"asdf\"",
            "      },",
            "      {});",
            "};"),
        "var MSG_F = function() {return'asdf'}",
        MESSAGE_NOT_INITIALIZED_CORRECTLY);
  }

  @Test
  public void testFunctionWithParamReplacementMissing() {
    multiPhaseTestWarning(
        "var MSG_G = function(measly) { return 'asdf' + measly};",
        lines(
            "var MSG_G = function(measly) {",
            "    return __jscomp_define_msg__(",
            "        {",
            "          \"key\":\"MSG_G\",",
            "          \"msg_text\":\"asdf{$measly}\"",
            "        },",
            "        {\"measly\":measly});",
            "    };"),
        "var MSG_G = function(measly) { return 'asdf' + measly}",
        MESSAGE_NOT_INITIALIZED_CORRECTLY);
  }

  @Test
  public void testPlaceholderNameInLowerUnderscoreCase() {
    multiPhaseTestPreLookupError(
        "var MSG_J = goog.getMsg('${$amt_earned}', {amt_earned: x});", MESSAGE_TREE_MALFORMED);
  }

  @Test
  public void testBadPlaceholderReferenceInReplacement() {
    registerMessage(getTestMessageBuilder("MSG_K").appendPlaceholderReference("amount").build());

    multiPhaseTestPostLookupError(
        lines(
            "/** @desc d */", //
            "var MSG_K = goog.getMsg('Hi {$jane}', {jane: x});",
            ""),
        lines(
            "/** @desc d */", //
            "var MSG_K =",
            "    __jscomp_define_msg__(",
            "        { \"key\":\"MSG_K\", \"msg_text\":\"Hi {$jane}\" },",
            "        {'jane': x});",
            ""),
        MESSAGE_TREE_MALFORMED);
  }

  @Test
  public void testEmptyObjLit() {
    registerMessage(getTestMessageBuilder("MSG_E").appendPlaceholderReference("amount").build());

    multiPhaseTestPostLookupError(
        lines(
            "/** @desc d */", //
            "var MSG_E = goog.getMsg('no placeholders');",
            ""),
        lines(
            "/** @desc d */", //
            "var MSG_E =",
            "    __jscomp_define_msg__({\"key\":\"MSG_E\", \"msg_text\":\"no placeholders\"});",
            ""),
        MESSAGE_TREE_MALFORMED,
        "Message parse tree malformed. "
            + "Empty placeholder value map for a translated message "
            + "with placeholders.");
  }

  @Test
  public void testLegacyStyleNoPlaceholdersVarSyntaxConcat() {
    registerMessage(getTestMessageBuilder("MSG_A").appendStringPart("Hi\nthere").build());
    multiPhaseTestWarning(
        "var MSG_A = 'abc' + 'def';", //
        "var MSG_A = __jscomp_define_msg__({\"key\":\"MSG_A\", \"msg_text\":\"abcdef\"});",
        "var MSG_A = 'Hi\\nthere'",
        MESSAGE_NOT_INITIALIZED_CORRECTLY);
  }

  @Test
  public void testLegacyStyleNoPlaceholdersVarSyntax() {
    registerMessage(getTestMessageBuilder("MSG_A").appendStringPart("Hi\nthere").build());
    multiPhaseTestWarning(
        "var MSG_A = 'd*6a0@z>t';", //
        "var MSG_A = __jscomp_define_msg__({\"key\":\"MSG_A\", \"msg_text\":\"d*6a0@z\\x3et\"});",
        "var MSG_A='Hi\\nthere'",
        MESSAGE_NOT_INITIALIZED_CORRECTLY);
  }

  @Test
  public void testLegacyStyleNoPlaceholdersFunctionSyntax() {
    registerMessage(getTestMessageBuilder("MSG_B").appendStringPart("Hi\nthere").build());
    multiPhaseTestWarning(
        "var MSG_B = function() {return 'asdf'};", //
        lines(
            "var MSG_B = function() {",
            "    return __jscomp_define_msg__(",
            "        {",
            "          \"key\":\"MSG_B\",",
            "          \"msg_text\":\"asdf\"",
            "        },",
            "        {});",
            "};"),
        "var MSG_B=function(){return'Hi\\nthere'}",
        MESSAGE_NOT_INITIALIZED_CORRECTLY);
  }

  @Test
  public void testLegacyStyleOnePlaceholder() {
    registerMessage(
        getTestMessageBuilder("MSG_C")
            .appendStringPart("One ")
            .appendPlaceholderReference("measly")
            .appendStringPart(" ph")
            .build());
    multiPhaseTestWarning(
        "var MSG_C = function(measly) {return 'asdf' + measly};",
        lines(
            "var MSG_C = function(measly) {",
            "    return __jscomp_define_msg__(",
            "        {",
            "          \"key\":\"MSG_C\",",
            "          \"msg_text\":\"asdf{$measly}\"",
            "        },",
            "        {\"measly\":measly});",
            "};"),
        "var MSG_C=function(measly){ return 'One ' + measly + ' ph'; }",
        MESSAGE_NOT_INITIALIZED_CORRECTLY);
  }

  @Test
  public void testLegacyStyleTwoPlaceholders() {
    registerMessage(
        getTestMessageBuilder("MSG_D")
            .appendPlaceholderReference("dick")
            .appendStringPart(" and ")
            .appendPlaceholderReference("jane")
            .build());
    multiPhaseTestWarning(
        "var MSG_D = function(jane, dick) {return jane + dick};", //
        lines(
            "var MSG_D = function(jane, dick) {",
            "    return __jscomp_define_msg__(",
            "        {",
            "          \"key\":\"MSG_D\",",
            "          \"msg_text\":\"{$jane}{$dick}\"",
            "        },",
            "        {\"jane\":jane, \"dick\":dick});",
            "};",
            ""),
        "var MSG_D = function(jane,dick) { return dick + ' and ' + jane; }",
        MESSAGE_NOT_INITIALIZED_CORRECTLY);
  }

  @Test
  public void testLegacyStylePlaceholderNameInLowerCamelCase() {
    registerMessage(
        getTestMessageBuilder("MSG_E")
            .appendStringPart("Sum: $")
            .appendPlaceholderReference("amtEarned")
            .build());
    multiPhaseTestWarning(
        "var MSG_E = function(amtEarned) {return amtEarned + 'x'};",
        lines(
            "var MSG_E = function(amtEarned) {",
            "    return __jscomp_define_msg__(",
            "        {",
            "          \"key\":\"MSG_E\",",
            "          \"msg_text\":\"{$amtEarned}x\"",
            "        },",
            "        {\"amtEarned\":amtEarned});",
            "};"),
        "var MSG_E=function(amtEarned){return'Sum: $'+amtEarned}",
        MESSAGE_NOT_INITIALIZED_CORRECTLY);
  }

  @Test
  public void testLegacyStylePlaceholderNameInLowerUnderscoreCase() {
    registerMessage(
        getTestMessageBuilder("MSG_F")
            .appendStringPart("Sum: $")
            .appendPlaceholderReference("amt_earned")
            .build());

    // Placeholder named in lower-underscore case (discouraged nowadays)
    multiPhaseTestWarning(
        "var MSG_F = function(amt_earned) {return amt_earned + 'x'};",
        lines(
            "var MSG_F = function(amt_earned) {",
            "    return __jscomp_define_msg__(",
            "        {",
            "          \"key\":\"MSG_F\",",
            "          \"msg_text\":\"{$amt_earned}x\"",
            "        },",
            "        {\"amt_earned\":amt_earned});",
            "};"),
        "var MSG_F=function(amt_earned){return'Sum: $'+amt_earned}",
        MESSAGE_NOT_INITIALIZED_CORRECTLY);
  }

  @Test
  public void testLegacyStyleBadPlaceholderReferenceInReplacement() {
    registerMessage(
        getTestMessageBuilder("MSG_B")
            .appendStringPart("Ola, ")
            .appendPlaceholderReference("chimp")
            .build());

    testWarning(
        "var MSG_B = function(chump) {return chump + 'x'};", MESSAGE_NOT_INITIALIZED_CORRECTLY);
  }

  @Test
  public void testTranslatedPlaceHolderMissMatch() {
    registerMessage(
        getTestMessageBuilder("MSG_A")
            .appendPlaceholderReference("a")
            .appendStringPart("!")
            .build());

    multiPhaseTestPreLookupError("var MSG_A = goog.getMsg('{$a}');", MESSAGE_TREE_MALFORMED);
  }

  @Test
  public void testTranslatedBadBooleanOptionValue() {
    registerMessage(
        getTestMessageBuilder("MSG_A")
            .appendPlaceholderReference("a")
            .appendStringPart("!")
            .build());

    multiPhaseTestPreLookupError(
        // used an object when a boolean is required
        "var MSG_A = goog.getMsg('{$a}', {'a': 'something'}, { html: {} });",
        MESSAGE_TREE_MALFORMED);
    multiPhaseTestPreLookupError(
        // used an object when a boolean is required
        "var MSG_A = goog.getMsg('{$a}', {'a': 'something'}, { unescapeHtmlEntities: {} });",
        MESSAGE_TREE_MALFORMED);
  }

  @Test
  public void testTranslatedMisspelledExamples() {
    registerMessage(
        getTestMessageBuilder("MSG_A")
            .appendPlaceholderReference("a")
            .appendStringPart("!")
            .build());

    multiPhaseTestPreLookupError(
        // mistakenly used "examples" instead of "example"
        "var MSG_A = goog.getMsg('{$a}', {'a': 'something'}, { examples: { 'a': 'example a' } });",
        MESSAGE_TREE_MALFORMED);
  }

  @Test
  public void testTranslatedMisspelledOriginalCode() {
    registerMessage(
        getTestMessageBuilder("MSG_A")
            .appendPlaceholderReference("a")
            .appendStringPart("!")
            .build());

    multiPhaseTestPreLookupError(
        // mistakenly used "original" instead of "original_code"
        "var MSG_A = goog.getMsg('{$a}', {'a': 'something'}, { original: { 'a': 'code' } });",
        MESSAGE_TREE_MALFORMED);
  }

  @Test
  public void testTranslatedExampleWithUnknownPlaceholder() {
    registerMessage(
        getTestMessageBuilder("MSG_A")
            .appendPlaceholderReference("a")
            .appendStringPart("!")
            .build());

    multiPhaseTestPreLookupError(
        "var MSG_A = goog.getMsg('{$a}', {'a': 'something'}, { example: { 'b': 'example a' } });",
        MESSAGE_TREE_MALFORMED);
  }

  @Test
  public void testTranslatedExampleWithNonStringPlaceholderValue() {
    registerMessage(
        getTestMessageBuilder("MSG_A")
            .appendPlaceholderReference("a")
            .appendStringPart("!")
            .build());

    multiPhaseTestPreLookupError(
        "var MSG_A = goog.getMsg('{$a}', {'a': 'something'}, { example: { 'a': 1 } });",
        MESSAGE_TREE_MALFORMED);
  }

  @Test
  public void testTranslatedExampleWithBadValue() {
    registerMessage(
        getTestMessageBuilder("MSG_A")
            .appendPlaceholderReference("a")
            .appendStringPart("!")
            .build());

    multiPhaseTestPreLookupError(
        "var MSG_A = goog.getMsg('{$a}', {'a': 'something'}, { example: 'bad value' });",
        MESSAGE_TREE_MALFORMED);
  }

  @Test
  public void testTranslatedExampleWithComputedProperty() {
    registerMessage(
        getTestMessageBuilder("MSG_A")
            .appendPlaceholderReference("a")
            .appendStringPart("!")
            .build());

    multiPhaseTestPreLookupError(
        // computed property is not allowed for examples
        "var MSG_A = goog.getMsg('{$a}', {'a': 'something'}, { example: { ['a']: 'wrong' } });",
        MESSAGE_TREE_MALFORMED);
  }

  @Test
  public void testBadFallbackSyntax1() {
    multiPhaseTestPreLookupError(
        lines(
            "/** @desc d */\n",
            "var MSG_A = goog.getMsg('asdf');",
            "var x = goog.getMsgWithFallback(MSG_A);"),
        JsMessageVisitor.BAD_FALLBACK_SYNTAX);
  }

  @Test
  public void testBadFallbackSyntax2() {
    multiPhaseTestPreLookupError(
        "var x = goog.getMsgWithFallback('abc', 'bcd');", JsMessageVisitor.BAD_FALLBACK_SYNTAX);
  }

  @Test
  public void testBadFallbackSyntax3() {
    multiPhaseTestPreLookupError(
        lines(
            "/** @desc d */\n",
            "var MSG_A = goog.getMsg('asdf');"
                + "var x = goog.getMsgWithFallback(MSG_A, NOT_A_MESSAGE);"),
        JsMessageVisitor.BAD_FALLBACK_SYNTAX);
  }

  @Test
  public void testBadFallbackSyntax4() {
    multiPhaseTestPreLookupError(
        lines(
            "/** @desc d */\n",
            "var MSG_A = goog.getMsg('asdf');"
                + "var x = goog.getMsgWithFallback(NOT_A_MESSAGE, MSG_A);"),
        JsMessageVisitor.BAD_FALLBACK_SYNTAX);
  }

  @Test
  public void testBadFallbackSyntax5() {
    multiPhaseTestPreLookupError(
        lines(
            "/** @desc d */\n",
            "var MSG_A = goog.getMsg('asdf');"
                + "var x = goog.getMsgWithFallback(MSG_A, MSG_DOES_NOT_EXIST);"),
        JsMessageVisitor.FALLBACK_ARG_ERROR);
  }

  @Test
  public void testBadFallbackSyntax6() {
    multiPhaseTestPreLookupError(
        lines(
            "/** @desc d */\n",
            "var MSG_A = goog.getMsg('asdf');"
                + "var x = goog.getMsgWithFallback(MSG_DOES_NOT_EXIST, MSG_A);"),
        JsMessageVisitor.FALLBACK_ARG_ERROR);
  }

  @Test
  public void testUseFallback() {
    registerMessage(getTestMessageBuilder("MSG_B").appendStringPart("translated").build());
    multiPhaseTest(
        lines(
            "/** @desc d */",
            "var MSG_A = goog.getMsg('msg A');",
            "/** @desc d */",
            "var MSG_B = goog.getMsg('msg B');",
            "var x = goog.getMsgWithFallback(MSG_A, MSG_B);"),
        lines(
            "/**",
            " * @desc d",
            " */",
            "var MSG_A =",
            "    __jscomp_define_msg__(",
            "        {",
            "          \"key\":\"MSG_A\",",
            "          \"msg_text\":\"msg A\",",
            "        });",
            "/**",
            " * @desc d",
            " */",
            "var MSG_B =",
            "    __jscomp_define_msg__(",
            "        {",
            "          \"key\":\"MSG_B\",",
            "          \"msg_text\":\"msg B\",",
            "        });",
            "var x = __jscomp_msg_fallback__(\"MSG_A\", MSG_A, \"MSG_B\", MSG_B);"),
        lines(
            "/** @desc d */",
            "var MSG_A = 'msg A';",
            "/** @desc d */",
            "var MSG_B = 'translated';",
            "var x = MSG_B;"));
  }

  @Test
  public void testFallbackEmptyBundle() {
    multiPhaseTest(
        lines(
            "/** @desc d */",
            "var MSG_A = goog.getMsg('msg A');",
            "/** @desc d */",
            "var MSG_B = goog.getMsg('msg B');",
            "var x = goog.getMsgWithFallback(MSG_A, MSG_B);"),
        lines(
            "/**",
            " * @desc d",
            " */",
            "var MSG_A =",
            "    __jscomp_define_msg__(",
            "        {",
            "          \"key\":\"MSG_A\",",
            "          \"msg_text\":\"msg A\",",
            "        });",
            "/**",
            " * @desc d",
            " */",
            "var MSG_B =",
            "    __jscomp_define_msg__(",
            "        {",
            "          \"key\":\"MSG_B\",",
            "          \"msg_text\":\"msg B\",",
            "        });",
            "var x = __jscomp_msg_fallback__(\"MSG_A\", MSG_A, \"MSG_B\", MSG_B);"),
        lines(
            "/** @desc d */",
            "var MSG_A = 'msg A';",
            "/** @desc d */",
            "var MSG_B = 'msg B';",
            "var x = MSG_A;"));
  }

  @Test
  public void testNoUseFallback() {
    registerMessage(getTestMessageBuilder("MSG_A").appendStringPart("translated").build());
    multiPhaseTest(
        lines(
            "/** @desc d */",
            "var MSG_A = goog.getMsg('msg A');",
            "/** @desc d */",
            "var MSG_B = goog.getMsg('msg B');",
            "var x = goog.getMsgWithFallback(MSG_A, MSG_B);"),
        lines(
            "/**",
            " * @desc d",
            " */",
            "var MSG_A =",
            "    __jscomp_define_msg__(",
            "        {",
            "          \"key\":\"MSG_A\",",
            "          \"msg_text\":\"msg A\",",
            "        });",
            "/**",
            " * @desc d",
            " */",
            "var MSG_B =",
            "    __jscomp_define_msg__(",
            "        {",
            "          \"key\":\"MSG_B\",",
            "          \"msg_text\":\"msg B\",",
            "        });",
            "var x = __jscomp_msg_fallback__(\"MSG_A\", MSG_A, \"MSG_B\", MSG_B);"),
        lines(
            "/** @desc d */",
            "var MSG_A = 'translated';",
            "/** @desc d */",
            "var MSG_B = 'msg B';",
            "var x = MSG_A;"));
  }

  @Test
  public void testNoUseFallback2() {
    registerMessage(getTestMessageBuilder("MSG_C").appendStringPart("translated").build());
    multiPhaseTest(
        lines(
            "/** @desc d */",
            "var MSG_A = goog.getMsg('msg A');",
            "/** @desc d */",
            "var MSG_B = goog.getMsg('msg B');",
            "var x = goog.getMsgWithFallback(MSG_A, MSG_B);"),
        lines(
            "/**",
            " * @desc d",
            " */",
            "var MSG_A =",
            "    __jscomp_define_msg__(",
            "        {",
            "          \"key\":\"MSG_A\",",
            "          \"msg_text\":\"msg A\",",
            "        });",
            "/**",
            " * @desc d",
            " */",
            "var MSG_B =",
            "    __jscomp_define_msg__(",
            "        {",
            "          \"key\":\"MSG_B\",",
            "          \"msg_text\":\"msg B\",",
            "        });",
            "var x = __jscomp_msg_fallback__(\"MSG_A\", MSG_A, \"MSG_B\", MSG_B);"),
        lines(
            "/** @desc d */",
            "var MSG_A = 'msg A';",
            "/** @desc d */",
            "var MSG_B = 'msg B';",
            "var x = MSG_A;"));
  }

  @Test
  public void testTemplateLiteralSimple() {
    registerMessage(getTestMessageBuilder("MSG_A").appendStringPart("Hi\nthere").build());

    multiPhaseTest(
        "/** @desc d */\n var MSG_A = goog.getMsg(`asdf`);",
        lines(
            "/**",
            " * @desc d",
            " */",
            "var MSG_A =",
            "    __jscomp_define_msg__(",
            "        {",
            "          \"key\":\"MSG_A\",",
            "          \"msg_text\":\"asdf\",",
            "        });",
            "     "),
        "/** @desc d */\n var MSG_A='Hi\\nthere'");
  }

  @Test
  public void testTemplateLiteralNameReplacement() {
    registerMessage(
        getTestMessageBuilder("MSG_B")
            .appendStringPart("One ")
            .appendPlaceholderReference("measly")
            .appendStringPart(" ph")
            .build());

    multiPhaseTest(
        "/** @desc d */\n var MSG_B=goog.getMsg(`asdf {$measly}`, {measly: x});",
        lines(
            "/**",
            " * @desc d",
            " */",
            "var MSG_B =",
            "    __jscomp_define_msg__(",
            "        {",
            "          \"key\":\"MSG_B\",",
            "          \"msg_text\":\"asdf {$measly}\",",
            "        },",
            "        {'measly': x});",
            ""),
        lines(
            "/** @desc d */", //
            "var MSG_B = 'One ' + x + ' ph';"));
  }

  @Test
  public void testTemplateLiteralSubstitutions() {
    // Only allow template literals that are constant strings
    registerMessage(getTestMessageBuilder("MSG_C").appendStringPart("Hi\nthere").build());

    multiPhaseTestPreLookupError(
        "/** @desc d */\n var MSG_C = goog.getMsg(`asdf ${42}`);",
        JsMessageVisitor.MESSAGE_TREE_MALFORMED);
  }

  @Test
  public void testReplaceUnescapeHtmlEntitiesMessage() {
    multiPhaseTest(
        "/** @desc d */\n var MSG_A = goog.getMsg('A', {}, {unescapeHtmlEntities: true});",
        lines(
            "/**",
            " * @desc d",
            " */",
            "var MSG_A =",
            "    __jscomp_define_msg__(",
            "        {",
            "          \"key\":\"MSG_A\",",
            "          \"msg_text\":\"A\",",
            "          \"unescapeHtmlEntities\":\"\"",
            "        },",
            "        {});",
            "     "),
        "/** @desc d */\n var MSG_A = 'A';");
    multiPhaseTest(
        lines(
            "/** @desc d */\n",
            "var MSG_A = goog.getMsg('User&apos;s &lt; email &amp; address &gt; are"
                + " &quot;correct&quot;', {}, {unescapeHtmlEntities: true});"),
        lines(
            "/**",
            " * @desc d",
            " */",
            "var MSG_A =",
            "    __jscomp_define_msg__(",
            "        {",
            "          \"key\":\"MSG_A\",",
            "          \"msg_text\":\"User\\x26apos;s \\x26lt; email \\x26amp; address \\x26gt;"
                + " are \\x26quot;correct\\x26quot;\",",
            "          \"unescapeHtmlEntities\":\"\"",
            "        },",
            "        {});",
            ""),
        "/** @desc d */\n var MSG_A = 'User\\'s < email & address > are \"correct\"';");
    multiPhaseTest(
        lines(
            "/** @desc d */\n",
            "var MSG_A = goog.getMsg('&lt; {$startSpan}email &amp; address{$endSpan} &gt;', "
                + "{'startSpan': '<span title=\"&lt;info&gt;\">', 'endSpan': '</span>'}, "
                + "{unescapeHtmlEntities: true});"),
        lines(
            "/**",
            " * @desc d",
            " */",
            "var MSG_A =",
            "    __jscomp_define_msg__(",
            "        {",
            "          \"key\":\"MSG_A\",",
            "          \"msg_text\":\"\\x26lt; {$startSpan}email \\x26amp; address{$endSpan}"
                + " \\x26gt;\",",
            "          \"unescapeHtmlEntities\":\"\"",
            "        },",
            "        {",
            "          \"startSpan\":'\\x3cspan title\\x3d\"\\x26lt;info\\x26gt;\"\\x3e',",
            "          \"endSpan\":\"\\x3c/span\\x3e\"",
            "        });",
            ""),
        lines(
            "/** @desc d */", //
            "var MSG_A =",
            "    '< ' + '<span title=\"&lt;info&gt;\">' + 'email & address' + '</span>' + ' >';",
            ""));
    multiPhaseTest(
        lines(
            "/** @desc d */\n",
            "var MSG_A = goog.getMsg('&amp;lt;double &amp;amp; escaping&amp;gt;', {},"
                + " {unescapeHtmlEntities: true});"),
        lines(
            "/**",
            " * @desc d",
            " */",
            "var MSG_A =",
            "    __jscomp_define_msg__(",
            "        {",
            "          \"key\":\"MSG_A\",",
            "          \"msg_text\":\"\\x26amp;lt;double \\x26amp;amp; escaping\\x26amp;gt;\",",
            "          \"unescapeHtmlEntities\":\"\"",
            "        },",
            "        {});",
            ""),
        "/** @desc d */\n var MSG_A = '&lt;double &amp; escaping&gt;';");
  }

  @Test
  public void testReplaceUnescapeHtmlEntitiesMessageWithReplacement() {
    registerMessage(
        getTestMessageBuilder("MSG_A")
            .appendStringPart("User")
            .appendStringPart("&")
            .appendStringPart("apos;s &")
            .appendStringPart("lt;")
            .appendStringPart(" email &a")
            .appendStringPart("mp; address &gt")
            .appendStringPart("; are &quot;correct")
            .appendStringPart("&quot;")
            .build());
    multiPhaseTest(
        "/** @desc d */\n var MSG_A = goog.getMsg('A', {}, {unescapeHtmlEntities: true});",
        lines(
            "/**",
            " * @desc d",
            " */",
            "var MSG_A =",
            "    __jscomp_define_msg__(",
            "        {",
            "          \"key\":\"MSG_A\",",
            "          \"msg_text\":\"A\",",
            "          \"unescapeHtmlEntities\":\"\"",
            "        },",
            "        {});",
            "     "),
        lines(
            "/** @desc d */", //
            "var MSG_A = 'User\\'s < email & address > are \"correct\"';"));

    registerMessage(
        getTestMessageBuilder("MSG_B")
            .appendStringPart("User")
            .appendStringPart("&apos;")
            .appendStringPart("s ")
            .appendStringPart("&lt;")
            .appendStringPart(" email ")
            .appendStringPart("&amp;")
            .appendStringPart(" address ")
            .appendStringPart("&gt;")
            .appendStringPart(" are ")
            .appendStringPart("&quot;")
            .appendStringPart("correct")
            .appendStringPart("&quot;")
            .build());
    multiPhaseTest(
        "/** @desc d */\n var MSG_B = goog.getMsg('B', {}, {unescapeHtmlEntities: true});",
        lines(
            "/**",
            " * @desc d",
            " */",
            "var MSG_B =",
            "    __jscomp_define_msg__(",
            "        {",
            "          \"key\":\"MSG_B\",",
            "          \"msg_text\":\"B\",",
            "          \"unescapeHtmlEntities\":\"\"",
            "        },",
            "        {});",
            ""),
        "/** @desc d */\n var MSG_B = 'User\\'s < email & address > are \"correct\"';");

    registerMessage(
        getTestMessageBuilder("MSG_C")
            .appendPlaceholderReference("br")
            .appendStringPart("&")
            .appendStringPart("amp;")
            .appendPlaceholderReference("x")
            .appendPlaceholderReference("y")
            .appendStringPart("&ap")
            .appendPlaceholderReference("z")
            .appendStringPart("os;")
            .build());
    multiPhaseTest(
        lines(
            "/** @desc d */\n",
            "var MSG_C = goog.getMsg('{$br}{$x}{$y}{$z}', {'br': '<br>', 'x': 'X', 'y': 'Y',"
                + " 'z': 'Z'}, {unescapeHtmlEntities: true});"),
        lines(
            "/**",
            " * @desc d",
            " */",
            "var MSG_C =",
            "    __jscomp_define_msg__(",
            "        {",
            "          \"key\":\"MSG_C\",",
            "          \"msg_text\":\"{$br}{$x}{$y}{$z}\",",
            "          \"unescapeHtmlEntities\":\"\"",
            "        },",
            "        {",
            "          \"br\":\"\\x3cbr\\x3e\",",
            "          \"x\":\"X\",",
            "          \"y\":\"Y\",",
            "          \"z\":\"Z\"",
            "        });",
            ""),
        lines(
            "/** @desc d */", //
            "var MSG_C = '<br>' + '&' + 'X' + 'Y' + '&ap' + 'Z' + 'os;';"));
  }

  @Test
  public void testReplaceHtmlMessageWithPlaceholder() {
    registerMessage(
        getTestMessageBuilder("MSG_A")
            .appendStringPart("Hello <") // html option changes `<` to `&lt;
            .appendPlaceholderReference("br")
            .appendStringPart("&gt;")
            .build());

    multiPhaseTest(
        "/** @desc d */\n var MSG_A = goog.getMsg('{$br}', {'br': '<br>'}, {html: true});",
        lines(
            "/**",
            " * @desc d",
            " */",
            "var MSG_A =",
            "    __jscomp_define_msg__(",
            "        {",
            "          \"key\":\"MSG_A\",",
            "          \"msg_text\":\"{$br}\",",
            "          \"escapeLessThan\":\"\",",
            "        },",
            "        {\"br\":\"\\x3cbr\\x3e\"});"),
        lines(
            "/** @desc d */", //
            "var MSG_A = 'Hello &lt;' + '<br>' + '&gt;';"));

    // Confirm that the default behavior is to leave `<` unchanged
    multiPhaseTest(
        "/** @desc d */\n var MSG_A = goog.getMsg('{$br}', {'br': '<br>'});",
        lines(
            "/**",
            " * @desc d",
            " */",
            "var MSG_A =",
            "    __jscomp_define_msg__(",
            "        {",
            "          \"key\":\"MSG_A\",",
            "          \"msg_text\":\"{$br}\",",
            "        },",
            "        {\"br\":\"\\x3cbr\\x3e\"});"),
        lines(
            "/** @desc d */", //
            "var MSG_A = 'Hello <' + '<br>' + '&gt;';"));
  }

  private void registerMessage(JsMessage message) {
    messages.put(message.getId(), message);
  }

  private class SimpleMessageBundle implements MessageBundle {

    @Override
    public JsMessage getMessage(String id) {
      return messages.get(id);
    }

    @Override
    public Iterable<JsMessage> getAllMessages() {
      return messages.values();
    }

    @Override
    public JsMessage.IdGenerator idGenerator() {
      return null;
    }
  }
}
