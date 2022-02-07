/** Nine different interpreters for Jam that differ in binding policy and cons evaluation policy.
  * The binding policy is either: call-by-value, call-by-name, or call-by-need.
  * The cons evaluation policy is either: call-by-value (eager), call-by-name (redundant lazy), or
  * call-by-need (efficient lazy).
  */

import java.io.IOException;
import java.io.Reader;


/** The exception class for Jam run-time errors during program evaluation. */
class EvalException extends RuntimeException {
  EvalException(String msg) { super(msg); }
}

/** Interpreter class supporting nine forms of evaluation for Jam programs.  These forms of evaluation differ in
  * binding policy and cons evaluation policy.
  * The binding policy is either: call-by-value, call-by-name, or call-by-need.
  * The cons evaluation policy is either: call-by-value (eager), call-by-name (redundant lazy), or
  * call-by-need (efficient lazy). */
class Interpreter {
  /** Parser to use. */
  Parser parser;  // initialized in constructors
  
  /** Parsed AST. */
  AST prog;       // initialized in constructors
  
  Interpreter(String fileName) throws IOException {
    parser = new Parser(fileName);
    prog = parser.parseAndCheck();
  }
  
  Interpreter(Parser p) {
    parser = p;
    prog = parser.parseAndCheck();
  }
  
  Interpreter(Reader reader) {
    parser = new Parser(reader);
    prog = parser.parseAndCheck();
  }
  
  /* Interpreter API: the public methods of this Jam Interpreter */
  
  /** Parses and ValueValue interprets the input embeded in parser, returning the result. */
  public JamVal callByValue() { return prog.accept(valueValueVisitor); }
  
  /** Parses and NameValue interprets the input embeded in parser, returning the result. */
  public JamVal callByName() { return prog.accept(nameValueVisitor); }
  
  /** Parses and NeedValue interprets the input embeded in parser, returning the result. */
  public JamVal callByNeed() { return prog.accept(needValueVisitor); }
  
  /** Parses and ValueValue interprets the input embeded in parser, returning the result. */
  public JamVal valueValue() { return prog.accept(valueValueVisitor); }
  
  /** Parses and ValueName interprets the input embeded in parser, returning the result. */
  public JamVal valueName() { return prog.accept(valueNameVisitor); }
  
  /** Parses and ValueNeed interprets the input embeded in parser, returning the result. */
  public JamVal valueNeed() {return prog.accept(valueNeedVisitor); }
  
  /** Parses and NameValue interprets the input embeded in parser, returning the result.  */
  public JamVal nameValue() { return prog.accept(nameValueVisitor); }
  
  /** Parses and NameName interprets the input embeded in parser, returning the result. */
  public JamVal nameName() { return prog.accept(nameNameVisitor); }
  
  /** Parses and NameNeed interprets the input embeded in parser, returning the result. */
  public JamVal nameNeed() { return prog.accept(nameNeedVisitor); }
  
  /** Parses and NeedValue interprets the input embeded in parser, returning the result. */
  public JamVal needValue() { return prog.accept(needValueVisitor); }
  
  /** Parses and NeedName interprets the input embeded in parser, returning the result. */
  public JamVal needName() { return prog.accept(needNameVisitor); }
  
  /** Parses and NeedNeed interprets the input embeded in parser, returning the result. */
  public JamVal needNeed() { return prog.accept(needNeedVisitor); }
  
  
  /* Interfaces that support different forms of Binding and different forms of list construction */
  
  /** The interface supported by various binding evaluation policies: call-by-value, call-by-name, and call-by-need. */
  interface BindingPolicy {  // formerly called EvalPolicy
    
    /** Constructs the appropriate binding object for this, binding var to ast in the evaluator ev. */
    Binding newBinding(Variable var, AST ast, EvalVisitor ev);
    
    /** Constructs the appropriate dummy binding object for this. */
    Binding newDummyBinding(Variable var);
  }
  
  /** Interface containing a factory to build the cons object specified by this ConsPolicy. */
  interface ConsPolicy {
    /** Constructs the appropriate cons given the arguments and corresponding EvalVisitor. */
    JamVal evalCons(AST[] args, EvalVisitor ev);
  }
  
  /* Note: Binding is defined in the file ValuesTokens because the JamClosure class depends on it. */
  
  /** Class representing a binding in CBV evaluation. */ 
  static class ValueBinding extends Binding {
    ValueBinding(Variable v, JamVal jv) { super(v, jv); }
    public JamVal value() { 
      if (value == null) illegalForwardReference(var());
      return value; 
    }
    public void setBinding(Suspension s) { value = s.eval(); }  // immediate evaluation
    public String toString() { return "[" + var + ", " + value + "]"; }
  }
  
  /** Class representing a binding in CBName evaluation. The inherited value field is ignored. */
  static class NameBinding extends Binding {
    protected Suspension susp;
    NameBinding(Variable v, Suspension s) { 
      super(v,null);
      susp = s;
    }
    public JamVal value() { return (susp == null) ? illegalForwardReference(var()) : susp.eval(); }
    public void setBinding(Suspension s) { susp = s; }
    public String toString() { return "[" + var + ", " + susp + "]"; }
  }
  
  /** Class representing a binding in CBNeed evaluation.  The inherited value field is used to hold the value
    * first computed by need .. */
  static class NeedBinding extends NameBinding {
    NeedBinding(Variable v, Suspension s) { super(v,s); }
    public JamVal value() {
      if (value == null) {  // a legitimate JamVal CANNOT be null
        if (susp == null) illegalForwardReference(var());
        else {             // Force the suspension and cache its value
          value = susp.eval();
          susp = null;     // release susp object for GC!
        }
      }
      return value;
    }
    public void setBinding(AST exp, EvalVisitor ev) { susp = new ConcreteSuspension(exp, ev); value = null; }
    public String toString() { return "[" + var + ", " + value + ", " + susp + "]"; }
  }
  
  /** Helper method supporting Binding classes */
  static JamVal illegalForwardReference(Variable v) {
    throw new EvalException("Attempt to evaluate variable " + v + " bound to null, indicating an illegal forward reference");
  }
  
  /** Binding policy for call-by-value. */
  static final BindingPolicy CALL_BY_VALUE = new BindingPolicy() {
    public Binding newBinding(Variable var, AST arg, EvalVisitor ev) { return new ValueBinding(var, arg.accept(ev)); }
    public Binding newDummyBinding(Variable var) { return new ValueBinding(var, null); } // null indicates still unbound
  };
  
  /** Binding policy for call-by-name. */
  static final BindingPolicy CALL_BY_NAME = new BindingPolicy() {
    public Binding newBinding(Variable var, AST arg, EvalVisitor ev) {
      return new NameBinding(var, new ConcreteSuspension(arg, ev));
    }
    public Binding newDummyBinding(Variable var) { return new NameBinding(var, null); } // null indicates still unbound
  };
  
  /** Binding policy for call-by-need. */
  static final BindingPolicy CALL_BY_NEED = new BindingPolicy() {
    public Binding newBinding(Variable var, AST arg, EvalVisitor ev) {
      return new NeedBinding(var, new ConcreteSuspension(arg, ev));
    }
    public Binding newDummyBinding(Variable var) { return new NeedBinding(var, null); } // null indicates still unbound
  };
  
  
  /** A class representing an unevaluated expresssion (together with the corresponding evaluator). */
  static class ConcreteSuspension implements Suspension {
    private AST exp;
    private EvalVisitor ev;  
    
    ConcreteSuspension(AST a, EvalVisitor e) { exp = a; ev = e; }
    
    AST exp() { return exp; }
    EvalVisitor ev() { return ev; }
    void putEv(EvalVisitor e) { ev = e; }
    
    /** Evaluates this suspension. Only method in Suspension interface */
    public JamVal eval() { 
      // System.err.println("eval() called on the susp with AST = " + exp);
      return exp.accept(ev);  } 
    
    public String toString() { return "<" + exp + ", " + ev + ">"; }
  }
  
  
  /** Class for a lazy cons structure. */
  static class JamLazyNameCons extends JamCons {
    /** Suspension for first */
    protected Suspension firstSusp;
    
    /** Suspension for rest */
    protected Suspension restSusp;
    
    public JamLazyNameCons(AST f, AST r, EvalVisitor ev) {
      super(null, null);
      firstSusp = new ConcreteSuspension(f, ev);
      restSusp = new ConcreteSuspension(r, ev);
    }
    
    public JamVal first() { return firstSusp.eval(); }
    public JamList rest() { return checkList(restSusp.eval()); }
  }
  
  /** Class for a lazy cons with optimization. */
  static class JamLazyNeedCons extends JamLazyNameCons {
    
    public JamLazyNeedCons(AST f, AST r, EvalVisitor ev) { super(f, r, ev); }
    
    public JamVal first() {
      if (first == null) {
        first = firstSusp.eval();
        firstSusp = null;
      }
      return first;
    }
    
    public JamList rest() {
      if (rest == null) {
        rest = checkList(restSusp.eval());
        restSusp = null;
      }
      return (JamList)rest;
    }
  }
  
  /** Eager cons evaluation policy. presume that args has exactly 2 elements. */
  public static final ConsPolicy EAGER = new ConsPolicy() {
    public JamVal evalCons(AST[] args, EvalVisitor ev) {
      JamVal val0 = args[0].accept(ev);
      JamVal val1 = args[1].accept(ev);
      if (val1 instanceof JamList) {
        return new JamCons(val0, (JamList)val1);
      }
      throw new EvalException("Second argument " + val1 + " to `cons' is not a JamList");
    }
  };
  
  /** Call-by-name lazy cons evaluation policy. */
  public static final ConsPolicy LAZYNAME = new ConsPolicy() {
    public JamVal evalCons(AST[] args, EvalVisitor ev) { return new JamLazyNameCons(args[0], args[1], ev); }
  };
  
  /** Call-by-need lazy cons evaluation policy. */
  public static final ConsPolicy LAZYNEED = new ConsPolicy() {
    public JamVal evalCons(AST[] args, EvalVisitor ev) { return new JamLazyNeedCons(args[0], args[1], ev); }
  };
  
  /** Value-value visitor. */
  static final ASTVisitor<JamVal> valueValueVisitor = new EvalVisitor(CALL_BY_VALUE, EAGER);
  
  /** Value-name visitor. */
  static final ASTVisitor<JamVal> valueNameVisitor = new EvalVisitor(CALL_BY_VALUE, LAZYNAME);
  
  /** Value-need visitor. */
  static final ASTVisitor<JamVal> valueNeedVisitor = new EvalVisitor(CALL_BY_VALUE, LAZYNEED);
  
  /** Name-value visitor. */
  static final ASTVisitor<JamVal> nameValueVisitor = new EvalVisitor(CALL_BY_NAME, EAGER);
  
  /** Name-name visitor. */
  static final ASTVisitor<JamVal> nameNameVisitor = new EvalVisitor(CALL_BY_NAME, LAZYNAME);
  
  /** Name-need visitor. */
  static final ASTVisitor<JamVal> nameNeedVisitor = new EvalVisitor(CALL_BY_NAME, LAZYNEED);
  
  /** Need-value visitor. */
  static final ASTVisitor<JamVal> needValueVisitor = new EvalVisitor(CALL_BY_NEED, EAGER);
  
  /** Need-name visitor. */
  static final ASTVisitor<JamVal> needNameVisitor = new EvalVisitor(CALL_BY_NEED, LAZYNAME);
  
  /** Need-need visitor. */
  static final ASTVisitor<JamVal> needNeedVisitor = new EvalVisitor(CALL_BY_NEED, LAZYNEED);
  
  
  /** Primary visitor class for performing interpretation. */
  static class EvalVisitor implements ASTVisitor<JamVal> {
    
    /* Assumes that:
     *   OpTokens are unique
     *   Variable objects are unique: v1.name.equals(v.name) => v1 == v2
     *   Only objects used as boolean values are BoolConstant.TRUE and BoolConstant.FALSE
     * Hence, == can be used to compare Variable objects, OpTokens, and BoolConstants
     */
    
    /** Environment. */
    PureList<Binding> env;
    
    /** Policy to create bindings. */
    BindingPolicy bindingPolicy;
    
    /** Policy to create cons. */
    ConsPolicy consPolicy;
    
    private EvalVisitor(PureList<Binding> e, BindingPolicy bp, ConsPolicy cp) {
      env = e;
      bindingPolicy = bp;
      consPolicy = cp;
    }
    
    public EvalVisitor(BindingPolicy bp, ConsPolicy cp) { this(new Empty<Binding>(), bp, cp); }
    
    /* EvalVisitor methods */
    
    /** Factory method that constructs a visitor similar to this with environment env. */
    public EvalVisitor newEvalVisitor(PureList<Binding> env) {
      return new EvalVisitor(env, bindingPolicy, consPolicy);
    }
    
    /** Getter for env field. */
    public PureList<Binding> env() { return env; }
    
    /* methods of ASTVisitor<JamVal> interface */
    
    public JamVal forBoolConstant(BoolConstant b) { return b; }
    public JamVal forIntConstant(IntConstant i) { return i; }
    public JamVal forEmptyConstant(EmptyConstant n) { return JamEmpty.ONLY; }
    
    public JamVal forVariable(Variable v) {
      Binding match = env.accept(new LookupVisitor<Binding>(v));
      if (match == null) {
        throw new EvalException("variable " + v + " is unbound");
      }
      return match.value();
    }
    
    public JamVal forPrimFun(PrimFun f) { return f; }
    public JamVal forUnOpApp(UnOpApp u) { return u.rator().accept(new UnOpEvaluator(u.arg().accept(this))); }
    public JamVal forBinOpApp(BinOpApp b) { return b.rator().accept(new BinOpEvaluator(b.arg1(), b.arg2())); }
    
    public JamVal forApp(App a) {
      JamVal rator = a.rator().accept(this);
      if (rator instanceof JamFun) 
        return ((JamFun)rator).accept(new FunEvaluator(a.args()));
      throw new EvalException(rator + " appears at head of application " + a + " but it is not a valid function");
    }
    
    public JamVal forMap(Map m) { return new JamClosure(m, env); }
    
    public JamVal forIf(If i) {
      JamVal test = i.test().accept(this);
      if (!(test instanceof BoolConstant))  throw new EvalException("non Boolean " + test + " used as test in if");
      if (test == BoolConstant.TRUE)  return i.conseq().accept(this);
      return i.alt().accept(this);
    }
    
    /* recursive let semantics */
    public JamVal forLet(Let l) {
      
      /* Extract binding vars and exps (rhs's) from l */
      Variable[] vars = l.vars();
      AST[] exps = l.exps();
      int n = vars.length;
      
      /* Construct newEnv for Let body and exps; vars are bound to values of corresponding exps using newEvalVisitor. */
      PureList<Binding> newEnv = env();
      Binding[] bindings = new Binding[n];
      for(int i = n - 1; i >= 0; i--) {
        bindings[i] = bindingPolicy.newDummyBinding(vars[i]);  // bind var[i] to dummy value null, indicating that var[i] is unbound
        newEnv = newEnv.cons(bindings[i]);                     // add new Binding to newEnv; it is shared!
      }
      /* create new EvalVisitor instance with new environment. */
      EvalVisitor newEV = newEvalVisitor(newEnv);
      
      /* Fix up the dummy values. */
      for(int i = 0; i < n; i++) {
        bindings[i].setBinding(new ConcreteSuspension(exps[i], newEV));  // modifies newEnv and newEvalVisitor
      }
      return l.body().accept(newEV);
    }
    
    /* Inner classes */
    
    /** Evaluates the application of a function to an array of argument ASTs. A true inner class that accesses the enclosing 
      * EvalVisitor instance. The applied function may be a JamClosure or a PrimFun*/
    class FunEvaluator implements JamFunVisitor<JamVal> {
      /** Unevaluated arguments */
      AST[] args;
      
      FunEvaluator(AST[] asts) { args = asts; }
      
      /** The anonymous inner class that evaluates PrimFun applications.  The evaluation of arguments has been deferred
        * for the sake of lazy cons. As a result, the evalArgs() method is called in most of the forXXXX methods. */
      PrimFunVisitor<JamVal> primEvaluator = new PrimFunVisitor<JamVal>() {
        
        /** Deferred evaluation of the args using the enclosing EvalVisitor. */
        private JamVal[] evalArgs() {
          int n = args.length;
          JamVal[] vals = new JamVal[n];
          for(int i = 0; i < n; i++) {
            vals[i] = args[i].accept(EvalVisitor.this);
          }
          return vals;
        }
        
        /** Throws an error.*/
        private void primFunError(String fn) {
          throw new EvalException("Primitive function `" + fn + "' applied to " + args.length + " arguments");
        }
        
        /** Evaluates an argument that has to be a Jam cons. */
        private JamCons evalJamConsArg(AST arg, String fun) {
          JamVal val = arg.accept(EvalVisitor.this);
          if (val instanceof JamCons) {
            return (JamCons)val;
          }
          throw new
            EvalException("Primitive function `" + fun + "' applied to argument " + val + " that is not a JamCons");
        }
        
        /* Visitor methods. */
        public JamVal forFunctionPPrim() {
          JamVal[] vals = evalArgs();
          if (vals.length != 1) primFunError("function?");
          return BoolConstant.toBoolConstant(vals[0] instanceof JamFun);
        }
        
        public JamVal forNumberPPrim() {
          JamVal[] vals = evalArgs();
          if (vals.length != 1) primFunError("number?");
          return BoolConstant.toBoolConstant(vals[0] instanceof IntConstant);
        }
        
        public JamVal forListPPrim() {
          JamVal[] vals = evalArgs();
          if (vals.length != 1) primFunError("list?");
          return BoolConstant.toBoolConstant(vals[0] instanceof JamList);
        }
        
        public JamVal forConsPPrim() {
          JamVal[] vals = evalArgs();
          if (vals.length != 1) primFunError("cons?");
          return BoolConstant.toBoolConstant(vals[0] instanceof JamCons);
        }
        
        public JamVal forEmptyPPrim() {
          JamVal[] vals = evalArgs();
          if (vals.length != 1) primFunError("null?");
          return BoolConstant.toBoolConstant(vals[0] instanceof JamEmpty);
        }
        
        public JamVal forConsPrim() {
          if (args.length != 2) primFunError("cons");
          return consPolicy.evalCons(args, EvalVisitor.this);   // Evaluation of args determined by consPolicy
        }
        
        public JamVal forArityPrim() {
          JamVal[] vals = evalArgs();
          if (vals.length != 1) primFunError("arity");
          if (!(vals[0] instanceof JamFun))  throw new EvalException("arity applied to argument " +  vals[0]);
          
          return ((JamFun)vals[0]).accept(new JamFunVisitor<IntConstant>() {
            public IntConstant forJamClosure(JamClosure jc) { return new IntConstant(jc.body().vars().length); }
            public IntConstant forPrimFun(PrimFun jpf) { return new IntConstant(jpf instanceof ConsPrim ? 2 : 1); }
          });
        }
        
        public JamVal forFirstPrim() { 
          if (args.length != 1) primFunError("first");
          return evalJamConsArg(args[0], "first").first(); 
        }
        public JamVal forRestPrim() { 
          if (args.length != 1) primFunError("rest");
          return evalJamConsArg(args[0], "rest").rest(); 
        }
      };  // end of anonymous inner class primFunEvaluator
      
      /* Support for JamFunVisitor<JamVal> interface */
      
      public JamVal forJamClosure(JamClosure closure) {
        Map map = closure.body();
        int n = args.length;
        Variable[] vars = map.vars();
        if (vars.length != n) {
          throw new EvalException("closure " + closure + " applied to " + n + " arguments");
        }
        /* Construct newEnv for JamClosure body using JamClosure env. */
        PureList<Binding> newEnv = closure.env();
        for(int i = n - 1; i >= 0; i--) {
          newEnv = newEnv.cons(bindingPolicy.newBinding(vars[i], args[i], EvalVisitor.this));
        }
        return map.body().accept(newEvalVisitor(newEnv));
      }
      
      /* Evaluates the primFun application.  The arguments cannot be evaluated yet because cons may be lazy. */
      public JamVal forPrimFun(PrimFun primFun) { return primFun.accept(primEvaluator); }
    }
    
    /** Evaluator for unary operators. Operand is already value (JamVal). */
    static class UnOpEvaluator implements UnOpVisitor<JamVal> {
      /** Value of the operand. */
      private JamVal val;
      
      UnOpEvaluator(JamVal jv) { val = jv; }
      
      /** Returns the value of the operand if it is an IntConstant; otherwise throws an exception. */
      private IntConstant checkInteger(String op) {
        if (val instanceof IntConstant)  return (IntConstant)val;
        throw new EvalException("Unary operator `" + op + "' applied to non-integer " + val);
      }
      
      /** Returns the value of the operand if it is a BoolConstant; otherwise throws an exception. */
      private BoolConstant checkBoolean(String op) {
        if (val instanceof BoolConstant)  return (BoolConstant)val;
        throw new EvalException("Unary operator `" + op + "' applied to non-boolean " + val);
      }
      
      /* Visitor methods in UnOpVisitor<JamVal> interface */
      public JamVal forUnOpPlus() { return checkInteger("+"); }
      public JamVal forUnOpMinus() { return new IntConstant(-checkInteger("-").value()); }
      public JamVal forOpTilde() { return checkBoolean("~").not(); }
    }
    
    /** Evaluator for binary operators. A true inner class that references the enclosing EvalVisior*/
    class BinOpEvaluator implements BinOpVisitor<JamVal> {
      /** Unevaluated arguments. */
      private AST arg1, arg2;
      
      BinOpEvaluator(AST a1, AST a2) {
        arg1 = a1;
        arg2 = a2;
      }
      
      /** Returns the value of arg if it is an IntConstant; otherwise throw an exception, */
      private IntConstant evalIntegerArg(AST arg, String b) {
        JamVal val = arg.accept(EvalVisitor.this);
        if (val instanceof IntConstant)  return (IntConstant)val;
        throw new EvalException("Binary operator `" + b + "' applied to non-integer " + val);
      }
      
      /** Returns the value of the argument if it is a BoolConstant, otherwise throw an exception, */
      private BoolConstant evalBooleanArg(AST arg, String b) {
        JamVal val = arg.accept(EvalVisitor.this);
        if (val instanceof BoolConstant)  return (BoolConstant)val;
        throw new EvalException("Binary operator `" + b + "' applied to non-boolean " + val);
      }
      
      /* Visitor methods */
      public JamVal forBinOpPlus() {
        return new IntConstant(evalIntegerArg(arg1,"+").value() + evalIntegerArg(arg2, "+").value());
      }
      
      public JamVal forBinOpMinus() {
        return new IntConstant(evalIntegerArg(arg1,"-").value() - evalIntegerArg(arg2, "-").value());
      }
      
      public JamVal forOpTimes() {
        return new IntConstant(evalIntegerArg(arg1,"*").value() * evalIntegerArg(arg2, "*").value());
      }
      
      public JamVal forOpDivide() {
        int divisor = evalIntegerArg(arg2, "/").value();
        if (divisor == 0) {
          throw new EvalException("Attempt to divide by zero");
        }
        return new IntConstant(evalIntegerArg(arg1,"/").value() / divisor);
      }
      
      public JamVal forOpEquals() {
        return BoolConstant.toBoolConstant(arg1.accept(EvalVisitor.this).equals(arg2.accept(EvalVisitor.this)));
      }
      
      public JamVal forOpNotEquals() {
        return BoolConstant.toBoolConstant(!arg1.accept(EvalVisitor.this).equals(arg2.accept(EvalVisitor.this)));
      }
      
      public JamVal forOpLessThan() {
        return BoolConstant.toBoolConstant(evalIntegerArg(arg1, "<").value() < evalIntegerArg(arg2, "<").value());
      }
      
      public JamVal forOpGreaterThan() {
        return BoolConstant.toBoolConstant(evalIntegerArg(arg1, ">").value() > evalIntegerArg(arg2, ">").value());
      }
      
      public JamVal forOpLessThanEquals() {
        return BoolConstant.toBoolConstant(evalIntegerArg(arg1, "<=").value() <= evalIntegerArg(arg2, "<=").value());
      }
      
      public JamVal forOpGreaterThanEquals() {
        return BoolConstant.toBoolConstant(evalIntegerArg(arg1, ">=").value() >= evalIntegerArg(arg2, ">=").value());
      }
      
      public JamVal forOpAnd() {
        BoolConstant b1 = evalBooleanArg(arg1, "&");
        if (b1 == BoolConstant.FALSE)  return BoolConstant.FALSE;
        return evalBooleanArg(arg2, "&");
      }
      
      public JamVal forOpOr() {
        BoolConstant b1 = evalBooleanArg(arg1, "|");
        if (b1 == BoolConstant.TRUE) return BoolConstant.TRUE;
        return evalBooleanArg(arg2, "|");
      }
    } // end of BinOpEvaluator class
  } // end of EvalVisitor class
} // end of Interpreter class
