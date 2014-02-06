/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.resolve.graphInference;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.graphInference.constraints.TypeEqualityConstraint;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;

public class FunctionalInterfaceParameterizationUtil {
  private static final Logger LOG = Logger.getInstance("#" + FunctionalInterfaceParameterizationUtil.class.getName());

  public static boolean isWildcardParameterized(@Nullable PsiType classType) {
    if (classType == null) return false;
    if (classType instanceof PsiIntersectionType) {
      for (PsiType type : ((PsiIntersectionType)classType).getConjuncts()) {
        if (!isWildcardParameterized(type)) return false;
      }
    }
    if (classType instanceof PsiClassType) {
      for (PsiType type : ((PsiClassType)classType).getParameters()) {
        if (type instanceof PsiWildcardType || type instanceof PsiCapturedWildcardType) {
          return true;
        }
      }
      return false;
    }
    return false;
  }

  @Nullable
  public static PsiType getGroundTargetType(@Nullable PsiType psiClassType, PsiLambdaExpression expr) {
    if (!isWildcardParameterized(psiClassType)) {
      return psiClassType;
    }

    if (expr.hasFormalParameterTypes()) return getFunctionalTypeExplicit(psiClassType, expr);

    return getFunctionalTypeImplicit(psiClassType);
  }

  private static PsiType getFunctionalTypeImplicit(PsiType psiClassType) {
    if (psiClassType instanceof PsiClassType) {
      final PsiClassType classType = (PsiClassType)psiClassType;
      final PsiClass psiClass = classType.resolve();
      if (psiClass != null) {

        final PsiTypeParameter[] typeParameters = psiClass.getTypeParameters();
        final HashSet<PsiTypeParameter> typeParametersSet = ContainerUtil.newHashSet(typeParameters);
        PsiType[] parameters = classType.getParameters();
        for (int i = 0; i < parameters.length; i++) {
          PsiType paramType = parameters[i];
          if (paramType instanceof PsiWildcardType) {
            final PsiClassType[] extendsListTypes = typeParameters[i].getExtendsListTypes();
            final PsiClassType Bi = extendsListTypes.length > 0 ? extendsListTypes[0]
                                                                : PsiType.getJavaLangObject(psiClass.getManager(), GlobalSearchScope.allScope(psiClass.getProject()));
            if (PsiPolyExpressionUtil.mentionsTypeParameters(Bi, typeParametersSet)) return null;

            final PsiType bound = ((PsiWildcardType)paramType).getBound();
            if (bound == null) {
              parameters[i] = Bi;
            } else if (((PsiWildcardType)paramType).isExtends()){
              parameters[i] = GenericsUtil.getGreatestLowerBound(Bi, bound);
            } else {
              parameters[i] = bound;
            }
          }
        }
        return JavaPsiFacade.getElementFactory(psiClass.getProject()).createType(psiClass, parameters);
      }
    }
    return psiClassType;
  }

  private static PsiType getFunctionalTypeExplicit(PsiType psiClassType, PsiLambdaExpression expr) {
    final PsiParameter[] lambdaParams = expr.getParameterList().getParameters();
    if (psiClassType instanceof PsiIntersectionType) {
      for (PsiType psiType : ((PsiIntersectionType)psiClassType).getConjuncts()) {
        final PsiType functionalType = getFunctionalTypeExplicit(psiType, expr);
        if (functionalType != null) return functionalType;
      }
      return null;
    }

    LOG.assertTrue(psiClassType instanceof PsiClassType, "Unexpected type: " + psiClassType);
    final PsiType[] parameters = ((PsiClassType)psiClassType).getParameters();
    final PsiClassType.ClassResolveResult resolveResult = ((PsiClassType)psiClassType).resolveGenerics();
    PsiClass psiClass = resolveResult.getElement();

    if (psiClass != null) {

      final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(resolveResult);
      if (interfaceMethod == null) return null;

      PsiTypeParameter[] typeParameters = psiClass.getTypeParameters();
      if (typeParameters.length != parameters.length) {
        return null;
      }
      final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(psiClass.getProject());
      final PsiParameter[] targetMethodParams = interfaceMethod.getParameterList().getParameters();
      if (targetMethodParams.length != lambdaParams.length) {
        return null;
      }

      final InferenceSession session = new InferenceSession(typeParameters, PsiSubstitutor.EMPTY, expr.getManager(), expr);

      for (int i = 0; i < targetMethodParams.length; i++) {
        session.addConstraint(new TypeEqualityConstraint(lambdaParams[i].getType(), targetMethodParams[i].getType()));
      }

      if (!session.repeatInferencePhases(false)) {
        return null;
      }

      final PsiSubstitutor substitutor = session.resolveBounds(true);

      final PsiType[] newTypeParameters = new PsiType[parameters.length];
      for (int i = 0; i < typeParameters.length; i++) {
        PsiTypeParameter typeParameter = typeParameters[i];
        final InferenceVariable variable = session.getInferenceVariable(typeParameter);
        final PsiType instantiation = variable.getInstantiation();
        if (instantiation != PsiType.NULL) {
          newTypeParameters[i] = instantiation;
        } else {
          newTypeParameters[i] = parameters[i];
        }
      }

      final PsiClassType parameterization = elementFactory.createType(psiClass, newTypeParameters);

      if (//todo !TypeConversionUtil.isAssignable(psiClassType, parameterization) || 
          !GenericsUtil.isTypeArgumentsApplicable(typeParameters, PsiSubstitutor.EMPTY.putAll(psiClass, newTypeParameters), expr)) {
        return null;
      }
      if (!TypeConversionUtil.containsWildcards(parameterization)) return parameterization;
    }
    return null;
  }
}
