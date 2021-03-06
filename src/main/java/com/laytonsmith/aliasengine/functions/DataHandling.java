/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.laytonsmith.aliasengine.functions;

import com.laytonsmith.aliasengine.api;
import com.laytonsmith.aliasengine.GenericTreeNode;
import com.laytonsmith.aliasengine.exceptions.CancelCommandException;
import com.laytonsmith.aliasengine.exceptions.ConfigRuntimeException;
import com.laytonsmith.aliasengine.Constructs.CArray;
import com.laytonsmith.aliasengine.Constructs.CArrayReference;
import com.laytonsmith.aliasengine.Constructs.CBoolean;
import com.laytonsmith.aliasengine.Constructs.CNull;
import com.laytonsmith.aliasengine.Constructs.CVoid;
import com.laytonsmith.aliasengine.Constructs.Construct;
import com.laytonsmith.aliasengine.Constructs.IVariable;
import com.laytonsmith.aliasengine.Env;
import com.laytonsmith.aliasengine.Globals;
import com.laytonsmith.aliasengine.Procedure;
import com.laytonsmith.aliasengine.exceptions.LoopBreakException;
import com.laytonsmith.aliasengine.exceptions.LoopContinueException;
import com.laytonsmith.aliasengine.Script;
import com.laytonsmith.aliasengine.Static;
import com.laytonsmith.aliasengine.functions.Exceptions.ExceptionType;
import com.laytonsmith.aliasengine.exceptions.FunctionReturnException;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 *
 * @author Layton
 */
public class DataHandling {
    public static String docs(){
        return "This class provides various methods to control script data and program flow.";
    }
    @api public static class array implements Function{

        public String getName() {
            return "array";
        }

        public Integer[] numArgs() {
            return new Integer[]{Integer.MAX_VALUE};
        }

        public Construct exec(int line_num, File f, Env env, Construct... args) throws CancelCommandException, ConfigRuntimeException {
            return new CArray(line_num, f, args);
        }
        
        public ExceptionType[] thrown(){
            return new ExceptionType[]{};
        }

        public String docs() {
            return "array {[var1, [var2...]]} Creates an array of values.";
        }

        public boolean isRestricted() {
            return false;
        }

        public void varList(IVariableList varList) {}

        public boolean preResolveVariables() {
            return true;
        }
        public String since() {
            return "3.0.1";
        }
        public Boolean runAsync() {
            return null;
        }
    }
    
    @api public static class assign implements Function{
        public String getName() {
            return "assign";
        }

        public Integer[] numArgs() {
            return new Integer[]{2};
        }

        public Construct exec(int line_num, File f, Env env, Construct... args) throws CancelCommandException, ConfigRuntimeException {
            Construct c = args[1];
            while(c instanceof IVariable){
                c = env.GetVarList().get(((IVariable)c).getName()).ival();
            }
            if(args[0] instanceof IVariable){
                IVariable v = new IVariable(((IVariable)args[0]).getName(), c, line_num, f);
                env.GetVarList().set(v);
                return v;
            }
            throw new ConfigRuntimeException("assign only accepts an ivariable or array reference as the first argument", ExceptionType.CastException, line_num, f);
        }
        private static class Chain {
            ArrayList<Construct> indexChain = new ArrayList<Construct>();
        }
        private void prepare(CArrayReference container, Chain c){
            if(container.array instanceof CArrayReference){
                prepare((CArrayReference)container.array, c);
                c.indexChain.add(container.index);
            } else {
                c.indexChain.add(container.index);               
            }
        }
        
        public Construct array_assign(int line_num, File f, Env env, Construct arrayAndIndex, Construct toSet){
            Construct ival = toSet;
            while(ival instanceof IVariable){
                ival = env.GetVarList().get(((IVariable)ival).getName()).ival();
            }
            Chain c = new Chain();
            prepare((CArrayReference)arrayAndIndex, c);
            CArray inner = (CArray)((CArrayReference)arrayAndIndex).getInternalArray();
            for(int i = 0; i < c.indexChain.size(); i++){
                if(i == c.indexChain.size() - 1){
                    //Last one, set it
                    inner.set(c.indexChain.get(i), ival);
                } else {
                    boolean makeIt = false;
                    Construct t = null;
                    if(!inner.contains(c.indexChain.get(i))){
                        makeIt = true;
                    } else {
                        t = inner.get(c.indexChain.get(i), line_num);
                        if(!(t instanceof CArray)){
                            makeIt = true;
                        }
                    }
                    if(makeIt){
                        Construct newArray = new CArray(line_num, f);
                        inner.set(c.indexChain.get(i), newArray);
                        t = newArray;
                    }
                    inner = (CArray)t;
                }
            }
            String name = ((CArrayReference)arrayAndIndex).name.getName();
            env.GetVarList().set(new IVariable(name, (CArray)((CArrayReference)arrayAndIndex).getInternalArray(), line_num, f));
            return new IVariable("=anon", ival, line_num, f);
        }
        
        public ExceptionType[] thrown(){
            return new ExceptionType[]{ExceptionType.CastException};
        }

        public String docs() {
            return "ivariable {ivar, mixed} Accepts an ivariable ivar as a parameter, and puts the specified value mixed in it. Returns the variable that was assigned.";
        }

        public boolean isRestricted() {
            return false;
        }

        public boolean preResolveVariables() {
            return false;
        }
        public String since() {
            return "3.0.1";
        }
        public Boolean runAsync() {
            return null;
        }
    }
    
    @api public static class _for implements Function{
        public String getName() {
            return "for";
        }

        public Integer[] numArgs() {
            return new Integer[]{4};
        }
        public Construct execs(int line_num, File f, Env env, Script parent, GenericTreeNode<Construct> assign, 
                GenericTreeNode<Construct> condition, GenericTreeNode<Construct> expression, 
                GenericTreeNode<Construct> runnable) throws CancelCommandException{
            Construct counter = parent.eval(assign, env);
            if(!(counter instanceof IVariable)){
                throw new ConfigRuntimeException("First parameter of for must be an ivariable", ExceptionType.CastException, line_num, f);
            }
            int _continue = 0;
            while(true){
                Construct cond = Static.resolveConstruct(parent.eval(condition, env).val(), line_num, f);
                if(!(cond instanceof CBoolean)){
                    throw new ConfigRuntimeException("Second parameter of for must return a boolean", ExceptionType.CastException, line_num, f);
                }
                CBoolean bcond = ((CBoolean) cond);
                if(bcond.getBoolean() == false){
                    break;
                }
                if(_continue >= 1){
                    --_continue;                    
                    parent.eval(expression, env);
                    continue;
                }
                try{
                    Static.resolveConstruct(parent.eval(runnable, env).val(), line_num, f);
                } catch(LoopBreakException e){
                    int num = e.getTimes();
                    if(num > 1){
                        e.setTimes(--num);
                        throw e;
                    }
                    return new CVoid(line_num, f);
                } catch(LoopContinueException e){
                    _continue = e.getTimes() - 1;                    
                    parent.eval(expression, env);
                    continue;
                }
                parent.eval(expression, env);
            }
            return new CVoid(line_num, f);
        }
        public ExceptionType[] thrown(){
            return new ExceptionType[]{ExceptionType.CastException};
        }
        public Construct exec(int line_num, File f, Env env, Construct... args) throws CancelCommandException, ConfigRuntimeException {
            return null;
        }

        public String docs() {
            return "void {assign, condition, expression1, expression2} Acts as a typical for loop. The assignment is first run. Then, a"
                    + " condition is checked. If that condition is checked and returns true, expression2 is run. After that, expression1 is run. In java"
                    + " syntax, this would be: for(assign; condition; expression1){expression2}. assign must be an ivariable, either a "
                    + "pre defined one, or the results of the assign() function. condition must be a boolean.";
        }

        public boolean isRestricted() {
            return false;
        }

        public boolean preResolveVariables() {
            return false;
        }
        public String since() {
            return "3.0.1";
        }
        //Doesn't matter, run out of state
        public Boolean runAsync() {
            return null;
        }
    }
    
    @api public static class foreach implements Function{
        public String getName() {
            return "foreach";
        }

        public Integer[] numArgs() {
            return new Integer[]{3};
        }

        public Construct exec(int line_num, File f, Env env, Construct... args) throws CancelCommandException, ConfigRuntimeException {
            return new CVoid(line_num, f);
        }
        
        public Construct execs(int line_num, File f, Env env, Script that, GenericTreeNode<Construct> array, 
                GenericTreeNode<Construct> ivar, GenericTreeNode<Construct> code) throws CancelCommandException{
            
            Construct arr = that.eval(array, env);
            if(arr instanceof IVariable){
                arr = env.GetVarList().get(((IVariable)arr).getName()).ival();
            }
            Construct iv = that.eval(ivar, env);
            
            if(arr instanceof CArray){
                if(iv instanceof IVariable){
                    CArray one = (CArray)arr;
                    IVariable two = (IVariable)iv;
                    for(int i = 0; i < one.size(); i++){
                        env.GetVarList().set(new IVariable(two.getName(), one.get(i, line_num), line_num, f));
                        try{
                            that.eval(code, env);
                        } catch(LoopBreakException e){
                            int num = e.getTimes();
                            if(num > 1){
                                e.setTimes(--num);
                                throw e;
                            }
                            return new CVoid(line_num, f);
                        } catch(LoopContinueException e){
                            i += e.getTimes() - 1;
                            continue;
                        }
                    }
                } else {
                    throw new ConfigRuntimeException("Parameter 2 of foreach must be an ivariable", ExceptionType.CastException, line_num, f);
                }
            } else {
                throw new ConfigRuntimeException("Parameter 1 of foreach must be an array", ExceptionType.CastException, line_num, f);
            }
            
            return new CVoid(line_num, f);
        }
        
        public ExceptionType[] thrown(){
            return new ExceptionType[]{ExceptionType.CastException};
        }

        public String docs() {
            return "void {array, ivar, code} Walks through array, setting ivar equal to each element in the array, then running code.";
        }

        public boolean isRestricted() {
            return false;
        }

        public boolean preResolveVariables() {
            return false;
        }
        public String since() {
            return "3.0.1";
        }
        //Doesn't matter, runs out of state anyways
        public Boolean runAsync(){
            return null;
        }
    }
    
    @api public static class _break implements Function{

        public String getName() {
            return "break";
        }

        public Integer[] numArgs() {
            return new Integer[]{0,1};
        }

        public String docs() {
            return "nothing {[int]} Stops the current loop. If int is specified, and is greater than 1, the break travels that many loops up. So, if you had"
                    + " a loop embedded in a loop, and you wanted to break in both loops, you would call break(2). If this function is called outside a loop"
                    + " (or the number specified would cause the break to travel up further than any loops are defined), the function will fail. If no"
                    + " argument is specified, it is the same as calling break(1).";
        }
        
        public ExceptionType[] thrown(){
            return new ExceptionType[]{ExceptionType.CastException};
        }

        public boolean isRestricted() {
            return false;
        }

        public void varList(IVariableList varList) {}

        public boolean preResolveVariables() {
            return true;
        }

        public String since() {
            return "3.1.0";
        }

        public Boolean runAsync() {
            return null;
        }

        public Construct exec(int line_num, File f, Env env, Construct... args) throws CancelCommandException, ConfigRuntimeException {
            int num = 1;
            if(args.length == 1){
                num = (int)Static.getInt(args[0]);
            }
            throw new LoopBreakException(num);
        }
        
    }
    
    @api public static class _continue implements Function{

        public String getName() {
            return "continue";
        }

        public Integer[] numArgs() {
            return new Integer[]{0, 1};
        }

        public String docs() {
            return "void {[int]} Skips the rest of the code in this loop, and starts the loop over, with it continuing at the next index. If this function"
                    + " is called outside of a loop, the command will fail. If int is set, it will skip 'int' repetitions. If no argument is specified,"
                    + " 1 is used.";
        }
        
        public ExceptionType[] thrown(){
            return new ExceptionType[]{ExceptionType.CastException};
        }

        public boolean isRestricted() {
            return false;
        }

        public void varList(IVariableList varList) {}

        public boolean preResolveVariables() {
            return true;
        }

        public String since() {
            return "3.1.0";
        }

        public Boolean runAsync() {
            return null;
        }

        public Construct exec(int line_num, File f, Env env, Construct... args) throws CancelCommandException, ConfigRuntimeException {
            int num = 1;
            if(args.length == 1){
                num = (int)Static.getInt(args[0]);
            }
            throw new LoopContinueException(num);
        }
        
    }
    
    @api public static class is_string implements Function{

        public String getName() {
            return "is_string";
        }

        public Integer[] numArgs() {
            return new Integer[]{1};
        }

        public String docs() {
            return "boolean {item} Returns whether or not the item is a string. Everything but arrays can be used as strings.";
        }

        public ExceptionType[] thrown() {
            return null;
        }

        public boolean isRestricted() {
            return false;
        }

        public void varList(IVariableList varList) {}

        public boolean preResolveVariables() {
            return true;
        }

        public String since() {
            return "3.1.2";
        }

        public Boolean runAsync() {
            return null;
        }

        public Construct exec(int line_num, File f, Env env, Construct... args) throws ConfigRuntimeException {
            return new CBoolean(!(args[0] instanceof CArray), line_num, f);
        }
        
    }
    
    @api public static class is_array implements Function{

        public String getName() {
            return "is_array";
        }

        public Integer[] numArgs() {
            return new Integer[]{1};
        }

        public String docs() {
            return "boolean {item} Returns whether or not the item is an array";
        }

        public ExceptionType[] thrown() {
            return null;
        }

        public boolean isRestricted() {
            return false;
        }

        public boolean preResolveVariables() {
            return true;
        }

        public String since() {
            return "3.1.2";
        }

        public Boolean runAsync() {
            return null;
        }

        public Construct exec(int line_num, File f, Env env, Construct... args) throws ConfigRuntimeException {
            return new CBoolean(args[0] instanceof CArray, line_num, f);
        }
        
    }
    
    @api public static class is_double implements Function{

        public String getName() {
            return "is_double";
        }

        public Integer[] numArgs() {
            return new Integer[]{1};
        }

        public String docs() {
            return "boolean {item} Returns whether or not the given item is a double. Note that a numeric string will return true, and so"
                    + " will integers.";
        }

        public ExceptionType[] thrown() {
            return null;
        }

        public boolean isRestricted() {
            return false;
        }

        public boolean preResolveVariables() {
            return true;
        }

        public String since() {
            return "3.1.2";
        }

        public Boolean runAsync() {
            return null;
        }

        public Construct exec(int line_num, File f, Env env, Construct... args) throws ConfigRuntimeException {
            boolean b = true;
            try{
                Static.getDouble(args[0]);
            } catch(ConfigRuntimeException e){
                b = false;
            }
            return new CBoolean(b, line_num, f);
        }
        
    }
    
    @api public static class is_integer implements Function{

        public String getName() {
            return "is_integer";
        }

        public Integer[] numArgs() {
            return new Integer[]{1};
        }

        public String docs() {
            return "boolean {item} Returns whether or not the given item is an integer. Note that numeric strings can be used as integers.";
        }

        public ExceptionType[] thrown() {
            return null;
        }

        public boolean isRestricted() {
            return false;
        }

        public void varList(IVariableList varList) {}

        public boolean preResolveVariables() {
            return true;
        }

        public String since() {
            return "3.1.2";
        }

        public Boolean runAsync() {
            return null;
        }

        public Construct exec(int line_num, File f, Env env, Construct... args) throws ConfigRuntimeException {
            boolean b = true;
            try{
                Static.getInt(args[0]);
            } catch(ConfigRuntimeException e){
                b = false;
            }
            return new CBoolean(b, line_num, f);
        }
        
    }
    
    @api public static class is_boolean implements Function{

        public String getName() {
            return "is_boolean";
        }

        public Integer[] numArgs() {
            return new Integer[]{1};
        }

        public String docs() {
            return "boolean {item} Returns whether the given item is a boolean. Note that all datatypes can be used as booleans, however"
                    + " null and arrays always return false. Essentially, this mean that this function ALWAYS returns true. Really, you"
                    + " probably shouldn't ever use it.";
        }

        public ExceptionType[] thrown() {
            return null;
        }

        public boolean isRestricted() {
            return false;
        }

        public void varList(IVariableList varList) {}

        public boolean preResolveVariables() {
            return true;
        }

        public String since() {
            return "3.1.2";
        }

        public Boolean runAsync() {
            return null;
        }

        public Construct exec(int line_num, File f, Env env, Construct... args) throws ConfigRuntimeException {
            return new CBoolean(true, line_num, f);
        }
        
    }
    
    @api public static class is_null implements Function{

        public String getName() {
            return "is_null";
        }

        public Integer[] numArgs() {
            return new Integer[]{1};
        }

        public String docs() {
            return "boolean {item} Returns whether or not the given item is null.";
        }

        public ExceptionType[] thrown() {
            return null;
        }

        public boolean isRestricted() {
            return false;
        }

        public void varList(IVariableList varList) {}

        public boolean preResolveVariables() {
            return true;
        }

        public String since() {
            return "3.1.2";
        }

        public Boolean runAsync() {
            return null;
        }

        public Construct exec(int line_num, File f, Env env, Construct... args) throws ConfigRuntimeException {
            return new CBoolean(args[0] instanceof CNull, line_num, f);
        }
        
    }
    //TODO: proc
    @api public static class proc implements Function{

        public String getName() {
            return "proc";
        }

        public Integer[] numArgs() {
            return new Integer[]{Integer.MAX_VALUE};
        }

        public String docs() {
            return "void {[name], [ivar...], procCode} Creates a new user defined procedure (also known as \"function\") that can be called later in code. Please see the more detailed"
                    + " documentation on procedures for more information.";
        }

        public ExceptionType[] thrown() {
            return new ExceptionType[]{ExceptionType.FormatException};
        }

        public boolean isRestricted() {
            return true;
        }

        public boolean preResolveVariables() {
            return false;
        }

        public String since() {
            return "3.1.3";
        }

        public Boolean runAsync() {
            return null;
        }
        
        public Construct exec(int line_num, File f, Env env, Construct... args) throws ConfigRuntimeException {
            return new CVoid(line_num, f);
        }
        
    }

    @api public static class _return implements Function{

        public String getName() {
            return "return";
        }

        public Integer[] numArgs() {
            return new Integer[]{0, 1};
        }

        public String docs() {
            return "nothing {mixed} Returns the specified value from this procedure. It cannot be called outside a procedure.";
        }

        public ExceptionType[] thrown() {
            return null;
        }

        public boolean isRestricted() {
            return false;
        }

        public void varList(IVariableList varList) {}

        public boolean preResolveVariables() {
            return true;
        }

        public String since() {
            return "3.2.0";
        }

        public Boolean runAsync() {
            return null;
        }

        public Construct exec(int line_num, File f, Env env, Construct... args) throws ConfigRuntimeException {
            Construct ret = (args.length == 1?args[0]:new CVoid(line_num, f));
            throw new FunctionReturnException(ret);
        }
        
    }
    
    @api public static class include implements Function{

        public String getName() {
            return "include";
        }

        public Integer[] numArgs() {
            return new Integer[]{1};
        }

        public String docs() {
            return "void {path} Includes external code at the specified path.";
        }

        public ExceptionType[] thrown() {
            return new ExceptionType[]{ExceptionType.IncludeException};
        }

        public boolean isRestricted() {
            return true;
        }

        public void varList(IVariableList varList) {}

        public boolean preResolveVariables() {
            return true;
        }

        public String since() {
            return "3.2.0";
        }

        public Boolean runAsync() {
            return true;
        }

        public Construct exec(int line_num, File f, Env env, Construct... args) throws ConfigRuntimeException {
            return new CVoid(line_num, f);
        }
        
        public Construct execs(int line_num, File f, Env env, List<GenericTreeNode<Construct>> children, Script parent){
            GenericTreeNode<Construct> tree = children.get(0);
            Construct arg = parent.eval(tree, env);
            arg = parent.preResolveVariables(new Construct[]{arg})[0];
            String location = arg.val();
            GenericTreeNode<Construct> include = IncludeCache.get(new File(location), line_num, f);
            parent.eval(include.getChildAt(0), env);
            return new CVoid(line_num, f);
        }
        
    }
    
    @api public static class call_proc implements Function{

        public String getName() {
            return "call_proc";
        }

        public Integer[] numArgs() {
            return new Integer[]{Integer.MAX_VALUE};
        }

        public String docs() {
            return "mixed {proc_name, [var1...]} Dynamically calls a user defined procedure. call_proc(_myProc, 'var1') is the equivalent of"
                    + " _myProc('var1'), except you could dynamically build the procedure name if need be. This is useful for having callbacks"
                    + " in procedures. Throws an InvalidProcedureException if the procedure isn't defined.";
        }

        public ExceptionType[] thrown() {
            return new ExceptionType[]{ExceptionType.InvalidProcedureException};
        }

        public boolean isRestricted() {
            return true;
        }

        public void varList(IVariableList varList) {}

        public boolean preResolveVariables() {
            return true;
        }

        public String since() {
            return "3.2.0";
        }

        public Boolean runAsync() {
            return null;
        }

        public Construct exec(int line_num, File f, Env env, Construct... args) throws ConfigRuntimeException {
            return new CVoid(line_num, f);
        }
        
        public Construct execs(int line_num, File f, Env env, Construct ... args){
            Procedure proc = env.GetProcs().get(args[0].val());
            if(proc != null){
                List<Construct> vars = new ArrayList<Construct>(Arrays.asList(args));
                vars.remove(0);                
                return proc.execute(vars, env);
            }            
            throw new ConfigRuntimeException("Unknown procedure \"" + args[0].val() + "\"", 
                    ExceptionType.InvalidProcedureException, line_num, f);
        }
        
    }
    
    @api public static class is_proc implements Function{

        public String getName() {
            return "is_proc";
        }

        public Integer[] numArgs() {
            return new Integer[]{1};
        }

        public String docs() {
            return "boolean {procName} Returns whether or not the given procName is currently defined, i.e. if calling this proc wouldn't"
                    + " throw an exception.";
        }

        public ExceptionType[] thrown() {
            return null;
        }

        public boolean isRestricted() {
            return true;
        }

        public void varList(IVariableList varList) {}

        public boolean preResolveVariables() {
            return true;
        }

        public String since() {
            return "3.2.0";
        }

        public Boolean runAsync() {
            return null;
        }
        
        public Construct exec(int line_num, File f, Env env, Construct ... args){
            return new CBoolean(env.GetProcs().get(args[0].val())==null?false:true, line_num, f);
        }
        
    }
    
    @api public static class is_associative implements Function{

        public String getName() {
            return "is_associative";
        }

        public Integer[] numArgs() {
            return new Integer[]{1};
        }

        public String docs() {
            return "boolean {array} Returns whether or not the array is associative. If the parameter is not an array, throws a CastException.";
        }

        public ExceptionType[] thrown() {
            return new ExceptionType[]{ExceptionType.CastException};
        }

        public boolean isRestricted() {
            return false;
        }

        public void varList(IVariableList varList) {}

        public boolean preResolveVariables() {
            return true;
        }

        public String since() {
            return "3.3.0";
        }

        public Boolean runAsync() {
            return null;
        }

        public Construct exec(int line_num, File f, Env env, Construct... args) throws ConfigRuntimeException {
            if(args[0] instanceof CArray){
                return new CBoolean(((CArray)args[0]).inAssociativeMode(), line_num, f);
            } else {
                throw new ConfigRuntimeException(this.getName() + " expects argument 1 to be an array", ExceptionType.CastException, line_num, f);
            }
        }
        
    }   
    
    @api public static class _import implements Function{

        public String getName() {
            return "import";
        }

        public Integer[] numArgs() {
            return new Integer[]{1};
        }

        public String docs() {
            return "mixed {ivar | string} This function imports a value from the global value"
                    + " register. In the first mode, it looks for an ivariable with the specified"
                    + " name, and stores the value in the variable, and returns void. In the"
                    + " second mode, it looks for a value stored with the specified key, and"
                    + " returns that value. Items can be stored with the export function. If"
                    + " the specified ivar doesn't exist, the ivar will be assigned an empty"
                    + " string, and if the specified string key doesn't exist, null is returned."
                    + " See the documentation on [[CommandHelper/import-export|imports/exports]]"
                    + " for more information.";
        }

        public ExceptionType[] thrown() {
            return new ExceptionType[]{};
        }

        public boolean isRestricted() {
            return true;
        }

        public boolean preResolveVariables() {
            return false;
        }

        public String since() {
            return "3.3.0";
        }

        public Boolean runAsync() {
            return null;
        }

        public Construct exec(int line_num, File f, Env environment, Construct... args) throws ConfigRuntimeException {
            if(args[0] instanceof IVariable){
                //Mode 1     
                IVariable var = (IVariable)args[0];
                environment.GetVarList().set(Globals.GetGlobalIVar(var));
                return new CVoid(line_num, f);
            } else {
                //Mode 2
                return Globals.GetGlobalConstruct(args[0].val());
            }
        }
        
    }
    
    @api public static class _export implements Function{

        public String getName() {
            return "export";
        }

        public Integer[] numArgs() {
            return new Integer[]{1, 2};
        }

        public String docs() {
            return "void {ivar | name, value} Stores a value in the global storage register."
                    + " When using the first mode, the ivariable is stored so it can be imported"
                    + " later, and when using the second mode, an arbitrary value is stored with"
                    + " the give key, and can be retreived using the secode mode of import. If"
                    + " the value is already stored, it is overwritten. See import() and"
                    + " [[CommandHelper/import-export|importing/exporting]]";
        }

        public ExceptionType[] thrown() {
            return new ExceptionType[]{ExceptionType.InsufficientArgumentsException};
        }

        public boolean isRestricted() {
            return true;
        }

        public boolean preResolveVariables() {
            return false;
        }

        public String since() {
            return "3.3.0";
        }

        public Boolean runAsync() {
            return null;
        }

        public Construct exec(int line_num, File f, Env environment, Construct... args) throws ConfigRuntimeException {
            if(args.length == 1){
                if(args[0] instanceof IVariable){
                    Globals.SetGlobal(environment.GetVarList().get(((IVariable)args[0]).getName()));
                } else { 
                    throw new ConfigRuntimeException("Expecting a IVariable when only one parameter is specified", ExceptionType.InsufficientArgumentsException, line_num, f);
                }
            } else {
                Globals.SetGlobal(args[0].val(), args[1]);
            }         
            return new CVoid(line_num, f);
        }
        
    }
    
}
