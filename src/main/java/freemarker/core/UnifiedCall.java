/*
 * Copyright (c) 2003 The Visigoth Software Society. All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution, if
 *    any, must include the following acknowledgement:
 *       "This product includes software developed by the
 *        Visigoth Software Society (http://www.visigoths.org/)."
 *    Alternately, this acknowledgement may appear in the software itself,
 *    if and wherever such third-party acknowledgements normally appear.
 *
 * 4. Neither the name "FreeMarker", "Visigoth", nor any of the names of the 
 *    project contributors may be used to endorse or promote products derived
 *    from this software without prior written permission. For written
 *    permission, please contact visigoths@visigoths.org.
 *
 * 5. Products derived from this software may not be called "FreeMarker" or "Visigoth"
 *    nor may "FreeMarker" or "Visigoth" appear in their names
 *    without prior written permission of the Visigoth Software Society.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE VISIGOTH SOFTWARE SOCIETY OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Visigoth Software Society. For more
 * information on the Visigoth Software Society, please see
 * http://www.visigoths.org/
 */

package freemarker.core;

import java.io.*;
import java.util.*;

import freemarker.template.*;

/**
 * An element for the unified macro/transform syntax. 
 */
final class UnifiedCall extends TemplateElement {

    private Expression nameExp;
    private Map namedArgs;
    private List positionalArgs, bodyParameterNames;
    boolean legacySyntax;

    UnifiedCall(Expression nameExp,
         Map namedArgs,
         TemplateElement nestedBlock,
         List bodyParameterNames) 
    {
        this.nameExp = nameExp;
        this.namedArgs = namedArgs;
        this.nestedBlock = nestedBlock;
        this.bodyParameterNames = bodyParameterNames;
    }

    UnifiedCall(Expression nameExp,
         List positionalArgs,
         TemplateElement nestedBlock,
         List bodyParameterNames) 
    {
        this.nameExp = nameExp;
        this.positionalArgs = positionalArgs;
        if (nestedBlock == TextBlock.EMPTY_BLOCK) {
            nestedBlock = null;
        }
        this.nestedBlock = nestedBlock;
        this.bodyParameterNames = bodyParameterNames;
    }


    void accept(Environment env) throws TemplateException, IOException {
        TemplateModel tm = nameExp.getAsTemplateModel(env);
        if (tm == Macro.DO_NOTHING_MACRO) return; // shortcut here.
        if (tm instanceof Macro) {
            Macro macro = (Macro) tm;
            if (macro.isFunction && !legacySyntax) {
                throw new TemplateException("Routine " + macro.getName() + 
                        " is a function. A function can only be called " +
                        "within the evaluation of an expression.", env);
            }    
            env.visit(macro, namedArgs, positionalArgs, bodyParameterNames,
                    nestedBlock);
        }
        else {
            boolean isDirectiveModel = tm instanceof TemplateDirectiveModel; 
            if (isDirectiveModel || tm instanceof TemplateTransformModel) {
                Map args;
                if (namedArgs != null && !namedArgs.isEmpty()) {
                    args = new HashMap();
                    for (Iterator it = namedArgs.entrySet().iterator(); it.hasNext();) {
                        Map.Entry entry = (Map.Entry) it.next();
                        String key = (String) entry.getKey();
                        Expression valueExp = (Expression) entry.getValue();
                        TemplateModel value = valueExp.getAsTemplateModel(env);
                        args.put(key, value);
                    }
                } else {
                    args = EmptyMap.instance;
                }
                if(isDirectiveModel) {
                    env.visit(nestedBlock, (TemplateDirectiveModel) tm, args, 
                            bodyParameterNames);
                }
                else { 
                    env.visit(nestedBlock, (TemplateTransformModel) tm, args);
                }
            }
            else if (tm == null) {
                throw new InvalidReferenceException(this.getStartLocation() + " " +
                        nameExp + " not found.", env);
            } else {
                throw new TemplateException(getStartLocation() + ": " + nameExp + 
                        " is not a user-defined directive. It is a " + 
                        tm.getClass().getName(), env);
            }
        }
    }

    public String getCanonicalForm() {
        StringBuilder buf = new StringBuilder("<@");
        buf.append(nameExp.getCanonicalForm());
        if (positionalArgs != null) {
            for (int i=0; i<positionalArgs.size(); i++) {
                Expression arg = (Expression) positionalArgs.get(i);
                if (i!=0) {
                    buf.append(',');
                }
                buf.append(' ');
                buf.append(arg.getCanonicalForm());
            }
        }
        else {
            ArrayList keys = new ArrayList(namedArgs.keySet());
            Collections.sort(keys);
            for (int i=0; i<keys.size();i++) {
                Expression arg = (Expression) namedArgs.get(keys.get(i));
                buf.append(' ');
                buf.append(keys.get(i));
                buf.append('=');
                buf.append(arg.getCanonicalForm());
            }
        }
        if (nestedBlock == null) {
            buf.append("/>");
        } 
        else {
            buf.append('>');
            buf.append(nestedBlock.getCanonicalForm());
            buf.append("</@");
            if (nameExp instanceof Identifier || (nameExp instanceof Dot && ((Dot) nameExp).onlyHasIdentifiers())) {
                buf.append(nameExp);
            }
            buf.append('>');
        }
        return buf.toString();
    }

    public String getDescription() {
        return "user-directive " + nameExp;
    }
/*
    //REVISIT
    boolean heedsOpeningWhitespace() {
        return nestedBlock == null;
    }

    //REVISIT
    boolean heedsTrailingWhitespace() {
        return nestedBlock == null;
    }*/
}
