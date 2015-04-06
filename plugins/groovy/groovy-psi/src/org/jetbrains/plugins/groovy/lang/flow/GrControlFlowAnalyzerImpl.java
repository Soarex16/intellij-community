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
package org.jetbrains.plugins.groovy.lang.flow;

import com.intellij.codeInspection.dataFlow.ControlFlow;
import com.intellij.codeInspection.dataFlow.ControlFlow.ControlFlowOffset;
import com.intellij.codeInspection.dataFlow.ControlFlowImpl;
import com.intellij.codeInspection.dataFlow.IControlFlowAnalyzer;
import com.intellij.codeInspection.dataFlow.Nullness;
import com.intellij.codeInspection.dataFlow.instructions.*;
import com.intellij.codeInspection.dataFlow.value.DfaRelation;
import com.intellij.codeInspection.dataFlow.value.DfaUnknownValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.flow.instruction.*;
import org.jetbrains.plugins.groovy.lang.flow.value.GrDfaValueFactory;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrCondition;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForInClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrTraditionalForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ControlFlowBuilderUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.Map;

import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mOPTIONAL_DOT;

public class GrControlFlowAnalyzerImpl<V extends GrInstructionVisitor<V>>
  extends GroovyRecursiveElementVisitor implements IControlFlowAnalyzer<V> {

  private static final Map<IElementType, DfaRelation> MAP = new HashMap<IElementType, DfaRelation>();

  static {
    MAP.put(GroovyTokenTypes.mEQUAL, DfaRelation.EQ);
    MAP.put(GroovyTokenTypes.mNOT_EQUAL, DfaRelation.NE);
    MAP.put(GroovyTokenTypes.kINSTANCEOF, DfaRelation.INSTANCEOF);
    MAP.put(GroovyTokenTypes.mGT, DfaRelation.GT);
    MAP.put(GroovyTokenTypes.mGE, DfaRelation.GE);
    MAP.put(GroovyTokenTypes.mLT, DfaRelation.LT);
    MAP.put(GroovyTokenTypes.mLE, DfaRelation.LE);
    MAP.put(GroovyTokenTypes.mPLUS, DfaRelation.PLUS);
  }

  private final ControlFlowImpl<V> myFlow = new ControlFlowImpl<V>();
  private final Stack<PsiElement> myElementStack = new Stack<PsiElement>();
  private final GrDfaValueFactory myFactory;
  private final PsiElement myCodeFragment;

  public GrControlFlowAnalyzerImpl(@NotNull GrDfaValueFactory factory, @NotNull PsiElement block) {
    myFactory = factory;
    myCodeFragment = block;
  }

  @Override
  public ControlFlow<V> buildControlFlow() {
    try {
      myCodeFragment.accept(new GroovyPsiElementVisitor(this) {
        @Override
        public void visitErrorElement(PsiErrorElement element) {
          throw new CannotAnalyzeException();
        }
      });
      //if (myFlow.getInstructionCount() == 0) {
      myFlow.addInstruction(new ReturnInstruction<V>(false, null));
      //}
      return myFlow;
    }
    catch (CannotAnalyzeException ignored) {
      return null;
    }
  }


  @Override
  public void visitOpenBlock(GrOpenBlock block) {
    startElement(block);
    super.visitOpenBlock(block);
    flushCodeBlockVariables(block);
    finishElement(block);
  }

  @Override
  public void visitBlockStatement(GrBlockStatement blockStatement) {
    startElement(blockStatement);
    super.visitBlockStatement(blockStatement);
    finishElement(blockStatement);
  }

  @Override
  public void visitVariableDeclaration(GrVariableDeclaration variableDeclaration) {
    startElement(variableDeclaration);

    final GrVariable[] variables = variableDeclaration.getVariables();
    if (variableDeclaration.isTuple()) {
      final GrExpression tupleInitializer = variableDeclaration.getTupleInitializer();
      if (tupleInitializer instanceof GrListOrMap) {
        final GrExpression[] initializers = ((GrListOrMap)tupleInitializer).getInitializers();
        // iterate over tuple variables and initialize each 
        for (int i = 0; i < Math.min(variables.length, initializers.length); i++) {
          initialize(variables[i], initializers[i]);
          pop();
        }
        // iterate over rest initializers and evaluate them
        for (int i = variables.length; i < initializers.length; i++) {
          initializers[i].accept(this);
          pop();
        }
      }
    }
    else {
      for (GrVariable variable : variables) {
        final GrExpression initializer = variable.getInitializerGroovy();
        if (initializer != null) {
          initialize(variable, initializer);
          pop();
        }
      }
    }

    finishElement(variableDeclaration);
  }

  @Override
  public void visitAssignmentExpression(GrAssignmentExpression expression) {
    startElement(expression);

    final GrExpression left = expression.getLValue();
    final GrExpression right = expression.getRValue();

    if (right == null) {
      pushUnknown();
      finishElement(expression);
      return;
    }

    final IElementType op = expression.getOperationTokenType();
    if (op == GroovyTokenTypes.mASSIGN) {
      if (left instanceof GrTupleExpression) {
        assignTuple(((GrTupleExpression)left).getExpressions(), right);
        pushUnknown(); // so there will be value to pop in finishElement()
      }
      else {
        assign(left, right);
      }
    }
    else {
      left.accept(this);
      addInstruction(new DupInstruction());
      right.accept(this);
      addInstruction(new BinopInstruction<V>(MAP.get(op), expression, expression.getProject()));
      addInstruction(new GrAssignInstruction<V>(myFactory.createValue(left), right, false));
    }

    finishElement(expression);
  }

  @Override
  public void visitMethodCallExpression(GrMethodCallExpression methodCallExpression) {
    startElement(methodCallExpression);

    // qualifier
    final GrExpression invokedExpression = methodCallExpression.getInvokedExpression();
    if (invokedExpression instanceof GrReferenceExpression) {
      final GrReferenceExpression reference = (GrReferenceExpression)invokedExpression;
      final GrExpression qualifier = reference.getQualifierExpression();
      if (qualifier != null) {
        qualifier.accept(this);
      }
      else {
        pushUnknown();
      }
    }
    else {
      pushUnknown();
    }

    // arguments
    for (GrNamedArgument argument : methodCallExpression.getNamedArguments()) {
      argument.accept(this);
    }
    for (GrExpression expression : methodCallExpression.getExpressionArguments()) {
      expression.accept(this);
    }
    for (GrClosableBlock block : methodCallExpression.getClosureArguments()) {
      push(myFactory.createValue(block), block);
      //addInstruction(new GotoInstruction<V>(myFlow.getEndOffset(block)));
      //block.accept(this);
    }

    addInstruction(new GrMethodCallInstruction<V>(methodCallExpression, null));

    finishElement(methodCallExpression);
  }

  @Override
  public void visitNewExpression(GrNewExpression expression) {
    startElement(expression);

    pushUnknown(); // qualifier

    final GrArrayDeclaration arrayDeclaration = expression.getArrayDeclaration();
    if (arrayDeclaration != null) {
      for (GrExpression dimension : arrayDeclaration.getBoundExpressions()) {
        dimension.accept(this);
        boxUnbox(dimension, PsiType.INT, dimension.getType());
        pop();
      }
    }
    else {
      final GrArgumentList args = expression.getArgumentList();
      final PsiMethod ctr = expression.resolveMethod();
      if (args != null) {
        final GrNamedArgument[] namedArguments = args.getNamedArguments();
        for (GrNamedArgument argument : namedArguments) {
          GrExpression argumentExpression = argument.getExpression();
          if (argumentExpression != null) {
            argumentExpression.accept(this);
            pop();
          }
        }
        if (namedArguments.length > 0) {
          pushUnknown();
        }

        final GrExpression[] expressionArguments = args.getExpressionArguments();
        final PsiParameter[] parameters = ctr == null ? null : ctr.getParameterList().getParameters();
        for (int i = 0; i < expressionArguments.length; i++) {
          final GrExpression argument = expressionArguments[i];
          argument.accept(this);
          if (parameters != null && i < parameters.length) {
            boxUnbox(argument, parameters[i].getType(), argument.getType());
          }
        }
      }

      addInstruction(new GrMethodCallInstruction(expression, null));

      //if (!myCatchStack.isEmpty()) {
      //  addMethodThrows(ctr, expression);
      //}
    }

    finishElement(expression);
  }

  @Override
  public void visitParenthesizedExpression(GrParenthesizedExpression expression) {
    startElement(expression);

    final GrExpression operand = expression.getOperand();
    if (operand == null) {
      throw new CannotAnalyzeException();
    }
    operand.accept(this);

    finishElement(expression);
  }

  @Override
  public void visitIfStatement(GrIfStatement statement) {
    startElement(statement);

    final GrExpression condition = statement.getCondition();
    final GrStatement thenBranch = statement.getThenBranch();
    final GrStatement elseBranch = statement.getElseBranch();
    final ControlFlowOffset ifFalseOffset = elseBranch != null
                                            ? myFlow.getStartOffset(elseBranch)
                                            : myFlow.getEndOffset(statement);

    if (condition != null) {
      condition.accept(this);
      addInstruction(new ConditionalGotoInstruction(ifFalseOffset, true, condition));
    }

    if (thenBranch != null) {
      thenBranch.accept(this);
    }

    if (elseBranch != null) {
      addInstruction(new GotoInstruction(myFlow.getEndOffset(statement)));
      elseBranch.accept(this);
    }

    finishElement(statement);
  }

  @Override
  public void visitForStatement(GrForStatement statement) {
    startElement(statement);
    final GrForClause clause = statement.getClause();
    final GrVariable parameter = clause == null ? null : clause.getDeclaredVariable();
    if (clause instanceof GrTraditionalForClause) {
      final GrTraditionalForClause traditionalForClause = (GrTraditionalForClause)clause;

      final GrCondition initialization = traditionalForClause.getInitialization();
      if (initialization != null) {
        initialization.accept(this);
        pop();
      }

      final GrExpression condition = traditionalForClause.getCondition();
      if (condition != null) {
        condition.accept(this);
      }
      else {
        pushUnknown();
      }
      addInstruction(new ConditionalGotoInstruction<V>(myFlow.getEndOffset(statement), true, condition));

      final GrStatement body = statement.getBody();
      if (body != null) {
        body.accept(this);
      }
      final GrExpression update = traditionalForClause.getUpdate();
      if (update != null) {
        update.accept(this);
        pop();
      }
      addInstruction(new GotoInstruction<V>(myFlow.getStartOffset(condition)));
    }
    else if (clause instanceof GrForInClause) {
      final GrForInClause forInClause = (GrForInClause)clause;

      final GrExpression iteratedValue = forInClause.getIteratedExpression();
      if (iteratedValue != null) {
        iteratedValue.accept(this);
        addInstruction(new GrMemberReferenceInstruction<V>(
          iteratedValue,
          myFactory.createValue(iteratedValue),
          false
        ));
      }

      final ControlFlowImpl.ControlFlowOffset loopStartOffset = myFlow.getNextOffset();
      removeVariable(parameter);

      pushUnknown();
      addInstruction(new ConditionalGotoInstruction(myFlow.getEndOffset(statement), true, null));

      final GrStatement body = statement.getBody();
      if (body != null) {
        body.accept(this);
      }

      addInstruction(new GotoInstruction(loopStartOffset));
    }
    
    finishElement(statement);
    removeVariable(parameter);
  }

  @Override
  public void visitParameter(GrParameter parameter) {
    startElement(parameter);
    final GrExpression initializer = parameter.getInitializerGroovy();
    if (initializer != null) {
      initialize(parameter, initializer);
      pop();
    }
    finishElement(parameter);
  }

  @Override
  public void visitElvisExpression(GrElvisExpression expression) {
    super.visitElvisExpression(expression);
  }

  @Override
  public void visitReferenceExpression(GrReferenceExpression referenceExpression) {
    startElement(referenceExpression);

    final boolean writing = PsiUtil.isAccessedForWriting(referenceExpression);
    final GrExpression qualifierExpression = referenceExpression.getQualifierExpression();
    if (qualifierExpression == null) {
      push(myFactory.createValue(referenceExpression), referenceExpression, writing);
    }
    else {
      qualifierExpression.accept(this);

      final GroovyResolveResult resolveResult = referenceExpression.advancedResolve();
      final PsiElement resolved = resolveResult.getElement();
      if (resolved instanceof PsiMethod && !(referenceExpression.getParent() instanceof GrMethodCallExpression) && !writing) {
        // groovy property access
        addInstruction(new GrMethodCallInstruction<V>(referenceExpression, (PsiMethod)resolved));
      }
      else {
        final DfaValue value = myFactory.createValue(referenceExpression);
        if (resolved instanceof PsiMember) {
          addInstruction(new GrMemberReferenceInstruction<V>(
            referenceExpression, value, referenceExpression.getDotTokenType() == mOPTIONAL_DOT
          ));
        }
        else {
          // pop qualifier if cannot resolve
          pop();
          // push value
          push(value, referenceExpression, writing);
        }
      }
    }

    finishElement(referenceExpression);
  }


  @Override
  public void visitUnaryExpression(GrUnaryExpression expression) {
    startElement(expression);

    GrExpression operand = expression.getOperand();
    if (operand != null) {
      operand.accept(this);
      IElementType tokenType = expression.getOperationTokenType();
      if (tokenType == GroovyTokenTypes.mLNOT) {
        addInstruction(new NotInstruction<V>());
      }
      else if (tokenType == GroovyTokenTypes.mBNOT) {

      }
      //} else if (tokenType == GroovyTokenTypes)
    }
    else {
      pushUnknown();
    }

    finishElement(expression);
  }

  @Override
  public void visitBinaryExpression(GrBinaryExpression expression) {
    startElement(expression);

    GrExpression left = expression.getLeftOperand();
    GrExpression right = expression.getRightOperand();

    if (right != null) {
      left.accept(this);
      right.accept(this);
      addInstruction(new BinopInstruction<V>(MAP.get(expression.getOperationTokenType()), expression, expression.getProject()));
    }
    else {
      pushUnknown();
    }

    finishElement(expression);
  }

  @Override
  public void visitInstanceofExpression(GrInstanceOfExpression expression) {
    startElement(expression);

    GrExpression operand = expression.getOperand();
    GrTypeElement typeElement = expression.getTypeElement();
    if (typeElement == null) {
      pushUnknown();
    }
    else {
      operand.accept(this);
      PsiType type = typeElement.getType();
      addInstruction(new PushInstruction<V>(myFactory.createTypeValue(type, Nullness.NOT_NULL), expression));
      addInstruction(new GrInstanceofInstruction<V>(operand, type));
    }

    finishElement(expression);
  }


  @Override
  public void visitLabeledStatement(GrLabeledStatement labeledStatement) {
    startElement(labeledStatement);
    final GrStatement childStatement = labeledStatement.getStatement();
    if (childStatement != null) {
      childStatement.accept(this);
    }
    finishElement(labeledStatement);
  }


  @Override
  public void visitReturnStatement(GrReturnStatement returnStatement) {
    startElement(returnStatement);

    final GrExpression returnValue = returnStatement.getReturnValue();
    if (returnValue != null) {
      returnValue.accept(this);
    }
    else {
      pushUnknown();
    }

    finishElement(returnStatement);
  }

  @Override
  public void visitLiteralExpression(GrLiteral literal) {
    startElement(literal);

    DfaValue dfaValue = myFactory.createLiteralValue(literal);
    push(dfaValue, literal);

    finishElement(literal);
  }


  private void initialize(@NotNull GrVariable variable, @NotNull GrExpression initializer) {
    final DfaVariableValue dfaVariableValue = myFactory.getVarFactory().createVariableValue(variable, false);
    push(dfaVariableValue, initializer);
    initializer.accept(this);
    boxUnbox(initializer, variable.getDeclaredType(), initializer.getNominalType());
    addInstruction(new GrAssignInstruction(dfaVariableValue, initializer, true));
  }

  private void assign(@NotNull GrExpression left, @NotNull GrExpression right) {
    left.accept(this);
    right.accept(this);
    boxUnbox(right, left.getType(), right.getType());
    addInstruction(new GrAssignInstruction(myFactory.createValue(left), right, false));
  }

  private void assign(@NotNull GrExpression left, @NotNull DfaValue right) {
    left.accept(this);
    push(right);
    addInstruction(new GrAssignInstruction<V>(myFactory.createValue(left), null, false));
  }

  private void assignTuple(@NotNull GrExpression[] lValues, @Nullable GrExpression right) {
    if (right instanceof GrListOrMap) {
      final GrExpression[] rValues = ((GrListOrMap)right).getInitializers();
      // iterate over tuple variables and assign each 
      for (int i = 0; i < Math.min(lValues.length, rValues.length); i++) {
        assign(lValues[i], rValues[i]);
        pop();
      }
      // iterate over rest lValues and assign them to null 
      for (int i = rValues.length; i < lValues.length; i++) {
        assign(lValues[i], myFactory.getConstFactory().getNull());
        pop();
      }
      // iterate over rest rValues and evaluate them
      for (int i = lValues.length; i < rValues.length; i++) {
        rValues[i].accept(this);
        pop();
      }
    }
    else {
      // here we cannot know what values will be assigned
      for (GrExpression lValue : lValues) {
        assign(lValue, DfaUnknownValue.getInstance());
      }
    }
  }

  private void boxUnbox(GrExpression context, PsiType expectedType, PsiType actualType) {
    if (TypeConversionUtil.isPrimitiveAndNotNull(expectedType) && TypeConversionUtil.isPrimitiveWrapper(actualType)) {
      addInstruction(new GrDummyInstruction<V>("UNBOXING"));
    }
    else if (TypeConversionUtil.isAssignableFromPrimitiveWrapper(expectedType) && TypeConversionUtil.isPrimitiveAndNotNull(actualType)) {
      addInstruction(new GrDummyInstruction<V>("BOXING"));
    }
  }

  private <T extends Instruction<V>> T addInstruction(T instruction) {
    myFlow.addInstruction(instruction);
    return instruction;
  }

  private void startElement(PsiElement element) {
    myFlow.startElement(element);
    myElementStack.push(element);
  }

  private void finishElement(GroovyPsiElement element) {
    myFlow.finishElement(element);
    PsiElement popped = myElementStack.pop();
    if (element != popped) {
      throw new AssertionError("Expected " + element + ", popped " + popped);
    }
    if (element instanceof GrExpression && ControlFlowBuilderUtil.isCertainlyReturnStatement((GrStatement)element)) {
      addInstruction(new CheckReturnValueInstruction<V>(
        element instanceof GrReturnStatement
        ? ((GrReturnStatement)element).getReturnValue()
        : element
      ));
      //addInstruction(new ReturnInstruction<V>(false, element));
    }
    else if (element instanceof GrStatement && element.getParent() instanceof GrCodeBlock) {
      if (element instanceof GrExpression) {
        pop();
      }
      addInstruction(new FinishElementInstruction(element));
    }
  }

  private void flushCodeBlockVariables(GrOpenBlock block) {
    for (GrStatement statement : block.getStatements()) {
      if (statement instanceof GrVariableDeclaration) {
        for (GrVariable variable : ((GrVariableDeclaration)statement).getVariables()) {
          removeVariable(variable);
        }
      }
    }
  }

  private void removeVariable(@Nullable GrVariable variable) {
    if (variable == null) return;
    addInstruction(new FlushVariableInstruction<V>(myFactory.getVarFactory().createVariableValue(variable, false)));
  }

  private PopInstruction pop() {
    return addInstruction(new PopInstruction());
  }

  private PushInstruction push(DfaValue value, PsiElement place) {
    return addInstruction(new PushInstruction(value, place));
  }

  private PushInstruction push(DfaValue value, PsiElement place, boolean writing) {
    return addInstruction(new PushInstruction<V>(value, place, writing));
  }

  private PushInstruction push(DfaValue value) {
    return push(value, null);
  }

  private void pushUnknown() {
    push(DfaUnknownValue.getInstance());
  }

  private void pushNull() {
    push(myFactory.getConstFactory().getNull());
  }
}
