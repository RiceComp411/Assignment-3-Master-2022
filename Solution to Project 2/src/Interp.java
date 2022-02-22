/** Call-by-value, Call-by-name, and Call-by-need Jam interpreter */

import java.io.IOException;
import java.io.Reader;

/** Interpreter Classes */

/** A class that implements call-by-value, call-by-name, and call-by-need interpretation of Jam programs. */
class Interpreter {
  
  /** program for this interpeter, initialized in constructors. */
  AST prog;
  
  /** Constructor for a program given in a file. */
  Interpreter(String fileName) throws IOException { 
    Parser p = new Parser(fileName);
    prog = p.parse();
  }
  
  /** Constructor for a program already embedded in a Parser object. */
  Interpreter(Parser p) { 
    prog = p.parse();
  }  
  
  /** Constructor for a program embedded in a Reader. */
  Interpreter(Reader reader) { 
    Parser p = new Parser(reader);
    prog = p.parse();
  }
  
  /** Parses and CBV interprets the input embeded in parser */
  public JamVal callByValue() { return prog.accept(valueEvalVisitor); }
  
  /** Parses and CBNm interprets the input embeded in parser */
  public JamVal callByName() { return prog.accept(nameEvalVisitor); }
  
  /** Parses and CBNd interprets the input embeded in parser */
  public JamVal callByNeed() { return prog.accept(needEvalVisitor); }
   
  /** A class representing an unevaluated expresssion (together with the corresponding evaluator). */
  static class ConcreteSuspension implements Suspension {
    private AST exp;
    private EvalVisitor ev;  
    
    ConcreteSuspension(AST a, EvalVisitor e) { exp = a; ev = e; }
    
    AST exp() { return exp; }
    EvalVisitor ev() { return ev; }
    void putEv(EvalVisitor e) { ev = e; }
    
    /** Evaluates this suspension. Only method of Suspension interface. */
    public JamVal eval() { 
      // System.err.println("eval() called on the susp with AST = " + exp);
      return exp.accept(ev);  } 
    
    public String toString() { return "<" + exp + ", " + ev + ">"; }
  }
  
  /** Class representing a binding in CBV evaluation. */
  static class ValueBinding extends Binding {
    ValueBinding(Variable v, JamVal jv) { super(v, jv); }
    public void setBinding(Suspension s) { value = s.eval(); }
    public String toString() { return "[" + var + ", " + value + "]"; }
  }
  
  /** Class representing a binding in CBName evaluation. The inherited value field is ignored. */
  static class NameBinding extends Binding {
    protected Suspension susp;
    NameBinding(Variable v, Suspension s) { 
      super(v, null);
      susp = s;
    }
    public JamVal value() { return susp.eval(); }
    public void setBinding(Suspension s) { susp = s; }
    public String toString() { return "[" + var + ", " + susp + "]"; }
  }
  
  /** Class representing a binding in CBNeed evaluation.  The inherited value field is used to hold the value
    * first computed by need .. */
  static class NeedBinding extends NameBinding {
    NeedBinding(Variable v, Suspension s) { super(v,s); }
    public JamVal value() {
      if (value == null) {  // null is not a legal JamVal; value is not yet defined.
        value = susp.eval();
        susp = null;  // release susp object for GC; modest optimization
      }
      return value;
    }
    public String toString() { return "[" + var + ", " + value + ", " + susp + "]"; }
  }
  
  /** Visitor class implementing a lookup method on environments.
    * @return value() for variable var for both lazy and eager environments. */
  static class LookupVisitor implements PureListVisitor<Binding,JamVal> {

    Variable var;   // the lexer guarantees that there is only one Variable for a given name
    
    LookupVisitor(Variable v) { var = v; }
    
    /* Visitor methods. */
    public JamVal forEmpty(Empty<Binding> e) { throw new EvalException("variable " + var + " is unbound"); }
    
    public JamVal forCons(Cons<Binding> c) {
      Binding b = c.first();
      if (var == b.var()) return b.value();
      return c.rest().accept(this);
    }
  }
 
  /** The interface supporting various evaluation policies (CBV, CBNm, CBNd) for map applications and let constructions. 
    * The EvalVisitor parameter appears in each method because the variable is bound to the value of the AST which is
    * determined using the specified EvalVisitor.  Note: all of the differences among CBV, CBNm, and CBNd evaluation can 
    * be encapulated in different implementations of the Binding interface.  Perhaps this interface should be called
    * BindingPolicy.  Since this interface is not part of the Interpreter API required by Assign2Test.java, you are free
    * to rename it if you choose.
    */
  interface EvalPolicy {
    /** Constructs the appropriate binding object for this, binding var to ast in the evaluator ev */
    Binding newBinding(Variable var, AST ast, EvalVisitor ev);
  }
  
  /** An ASTVisitor class for evaluating ASTs where the evaluation policy for function applications and other
    * binding operations such as let are determined by an embedded EvalPolicy. */
  static class EvalVisitor implements ASTVisitor<JamVal> {
    
    /* The code in this class assumes that:
     * * OpTokens are unique; 
     * * Variable objects are unique: v1.name.equals(v.name) => v1 == v2; and
     * * The only objects used as boolean values are BoolConstant.TRUE and BoolConstant.FALSE.
     * Hence,  == can be used to compare Variable objects, OpTokens, and BoolConstants. */
    
    PureList<Binding> env;  // the embdedded environment
    EvalPolicy evalPolicy;  // the embedded EvalPolicy
    
    /** Constructor for recursive calls. */
    private EvalVisitor(PureList<Binding> e, EvalPolicy ep) { 
      env = e; 
      evalPolicy = ep; 
    }
    
    /** Top level constructor. */
    public EvalVisitor(EvalPolicy ep) { this(new Empty<Binding>(), ep); }
    
    /** Factory method that constructs a new visitor with environment e and same evalPolicy as this.  It is used
      * for recursive invocations of this evaluator.  Essential in some contexts because it has access to evalPolicy. */
    public EvalVisitor newVisitor(PureList<Binding> e) { return new EvalVisitor(e, evalPolicy); }
    
    /** Factory method that constructs a Binding of var to ast corresponding to this.evalPolicy */
    public Binding newBinding(Variable var, AST ast) { return evalPolicy.newBinding(var, ast, this); }
    
    /** Getter for env field */
    public PureList<Binding> env() { return env; }
    
    /* EvalVisitor methods */
    public JamVal forBoolConstant(BoolConstant b) { return b; }
    public JamVal forIntConstant(IntConstant i) { return i; }
    public JamVal forEmptyConstant(EmptyConstant n) { return JamEmpty.ONLY; }
    public JamVal forVariable(Variable v) {  return env.accept(new LookupVisitor(v)); }
    
    public JamVal forPrimFun(PrimFun f) { return f; }
    
    public JamVal forUnOpApp(UnOpApp u) { 
      return u.rator().accept(new StandardUnOpVisitor(u.arg().accept(this)));
    }
    
    public JamVal forBinOpApp(BinOpApp b) {
      return b.rator().accept(new StandardBinOpVisitor(b.arg1(), b.arg2(), this));
    }
    
    public JamVal forApp(App a) {
      JamVal rator = a.rator().accept(this);
      if (rator instanceof JamFun) return ((JamFun) rator).accept(new StandardFunVisitor(a.args(), this));
      throw new EvalException(rator + " appears at head of application " + a + " but it is not a valid function");
    }
    
    public JamVal forMap(Map m) { return new JamClosure(m,env); }
    
    public JamVal forIf(If i) {
      JamVal test = i.test().accept(this);
      if (! (test instanceof BoolConstant)) throw new EvalException("non Boolean " + test + " used as test in if");
      if (test == BoolConstant.TRUE) return i.conseq().accept(this);
      return i.alt().accept(this);
    }
    
    /* let (non-recursive) semantics */
    public JamVal forLet(Let l) {
      Def[] defs = l.defs();
      int n = defs.length;
      
      Variable[] vars = new Variable[n];
      for (int i = 0; i < n; i++) vars[i] = defs[i].lhs();
      
      AST[] exps =  new AST[n];
      for (int i = 0; i < n; i++) exps[i] = defs[i].rhs();
   
      // construct newEnv for Let body; vars are bound to values of corresponding exps using evalVisitor
      PureList<Binding> newEnv = env();
      for (int i = 0; i < n; i++) newEnv = newEnv.cons(newBinding(vars[i], exps[i]));
      
      EvalVisitor newEvalVisitor = newVisitor(newEnv);  
      
      return l.body().accept(newEvalVisitor);
    }
  }
  
  /** Top-level EvalVisitors implementing CBV, CBNm, and CBNd evaluation. */
  static EvalVisitor valueEvalVisitor = new EvalVisitor(CallByValue.ONLY);
  static EvalVisitor nameEvalVisitor = new EvalVisitor(CallByName.ONLY);
  static EvalVisitor needEvalVisitor = new EvalVisitor(CallByNeed.ONLY);
  
  /** Class that implements the evaluation of function applications given the embedded arguments and evalVisitor. */
  static class StandardFunVisitor implements JamFunVisitor<JamVal> {
    
    /** Unevaluated arguments */
    AST[] args;
    
    /** Evaluation visitor */
    EvalVisitor evalVisitor;
    
    StandardFunVisitor(AST[] asts, EvalVisitor ev) {
      args = asts;
      evalVisitor = ev;
    }
    
    /** Evaluate the arguments of a PrimFun application. */
    private JamVal[] evalArgs() {
      int n = args.length;
      JamVal[] vals = new JamVal[n];
      for (int i=0; i < n; i++) vals[i] = args[i].accept(evalVisitor);
      return vals;
    }
    
    /* Visitor methods. */
    public JamVal forJamClosure(JamClosure closure) {
      Map map = closure.body();
      
      int n = args.length;
      Variable[] vars = map.vars();
      if (vars.length != n) 
        throw new EvalException("closure " + closure + " applied to " + n + " arguments");
      
      /* Construct newEnv for JamClosure body using JamClosure env; the order of vars is reversed so that the first
       * var in vars appears at the front of the new environment. */
      
      PureList<Binding> newEnv = closure.env();
      for (int i = 0; i < n; i++) 
        newEnv = newEnv.cons(evalVisitor.newBinding(vars[i], args[i]));
      return map.body().accept(evalVisitor.newVisitor(newEnv));
    }
   
    public JamVal forPrimFun(PrimFun primFun) {
      // Note: all primFuns evaluate all of their arguments
      JamVal[] jamVals = evalArgs();  // private method evalArgs accesses the args field which has type AST[]
      return primFun.accept(new StandardPrimFunVisitor(jamVals)); 
    }
  }
  
  static class CallByValue implements EvalPolicy {
    
    public static final EvalPolicy ONLY = new CallByValue();
    private CallByValue() { }
    
    /** Constructs binding of var to value of arg in ev */
    public Binding newBinding(Variable var, AST arg, EvalVisitor ev) { return new ValueBinding(var, arg.accept(ev)); }
  }
    
  static class CallByName implements EvalPolicy {
    public static final EvalPolicy ONLY = new CallByName();
    private CallByName() {}
    
    public Binding newBinding(Variable var, AST arg, EvalVisitor ev) { return new NameBinding(var, new ConcreteSuspension(arg, ev)); }
  }
  
  static class CallByNeed implements EvalPolicy {
    public static final EvalPolicy ONLY = new CallByNeed();
    private CallByNeed() {}
    
    public Binding newBinding(Variable var, AST arg, EvalVisitor ev) { return new NeedBinding(var, new ConcreteSuspension(arg, ev)); }
  }
  
  /* Only works for eager unary operators; need to add a newUnOpVisitor method to EvalPolicy if lazy unary operator is
   * added to Jam*/
  static class StandardUnOpVisitor implements UnOpVisitor<JamVal> {
    private final JamVal val;
    StandardUnOpVisitor(JamVal jv) { val = jv; }
    
    private IntConstant checkInteger(String op) {
      if (val instanceof IntConstant) return (IntConstant) val;
      throw new EvalException("Unary operator `" + op + "' applied to non-integer " + val);
    }
    
    private BoolConstant checkBoolean(String op) {
      if (val instanceof BoolConstant) return (BoolConstant) val;
      throw new EvalException("Unary operator `" + op + "' applied to non-boolean " + val);
    }

    public JamVal forUnOpPlus() { return checkInteger("+"); } // CheckInteger returns val; 
    public JamVal forUnOpMinus() { return new IntConstant(- checkInteger("-").value()); } // CheckInteger returns Jamval; 
   
    public JamVal forOpTilde() { return checkBoolean("~").not(); }
    // public JamVal forOpBang() { return ... ; }  // Supports addition of ref cells to Jam
    // public JamVal forOpRef() { return ... ; }   // Supports addition of ref cells to Jam
  }
  
  static class StandardBinOpVisitor implements BinOpVisitor<JamVal> { 
    private AST arg1, arg2;
    private EvalVisitor evalVisitor;
    
    StandardBinOpVisitor(AST a1, AST a2, EvalVisitor ev) { arg1 = a1; arg2 = a2; evalVisitor = ev; }
    
    private IntConstant evalIntegerArg(AST arg, String op) {
      JamVal val = arg.accept(evalVisitor);
      if (val instanceof IntConstant) return (IntConstant) val;
      throw new EvalException("Binary operator `" + op + "' applied to non-integer " + val);
    }
    
    private BoolConstant evalBooleanArg(AST arg, String op) {
      JamVal val = arg.accept(evalVisitor);
      if (val instanceof BoolConstant) return (BoolConstant) val;
      throw new EvalException("Binary operator `" + op + "' applied to non-boolean " + val);
    }
    
    public JamVal forBinOpPlus() {
      return new IntConstant(evalIntegerArg(arg1,"+").value() + evalIntegerArg(arg2,"+").value());
    }
    public JamVal forBinOpMinus() {
      return new IntConstant(evalIntegerArg(arg1,"-").value() - evalIntegerArg(arg2,"-").value());
    }
    
    public JamVal forOpTimes() {
      return new IntConstant(evalIntegerArg(arg1,"*").value() * evalIntegerArg(arg2,"*").value());
    }
    
    public JamVal forOpDivide() {
      return new IntConstant(evalIntegerArg(arg1,"/").value() / evalIntegerArg(arg2,"/").value());
    }
    
    public JamVal forOpEquals() {
      return BoolConstant.toBoolConstant(arg1.accept(evalVisitor).equals(arg2.accept(evalVisitor)));
    }
    
    public JamVal forOpNotEquals() {
      return BoolConstant.toBoolConstant(! arg1.accept(evalVisitor).equals(arg2.accept(evalVisitor)));
    }
    
    public JamVal forOpLessThan() {
      return BoolConstant.toBoolConstant(evalIntegerArg(arg1,"<").value() < evalIntegerArg(arg2,"<").value());
    }
    
    public JamVal forOpGreaterThan() {
      return BoolConstant.toBoolConstant(evalIntegerArg(arg1,">").value() > evalIntegerArg(arg2,">").value());
    }
    
    public JamVal forOpLessThanEquals() {
      return BoolConstant.toBoolConstant(evalIntegerArg(arg1,"<=").value() <= evalIntegerArg(arg2,"<=").value());
    }
    
    public JamVal forOpGreaterThanEquals() {
      return BoolConstant.toBoolConstant(evalIntegerArg(arg1,">=").value() >= evalIntegerArg(arg2,">=").value());
    }
    
    public JamVal forOpAnd() {
      BoolConstant b1 = evalBooleanArg(arg1,"&");
      if (b1 == BoolConstant.FALSE) return BoolConstant.FALSE;
      return evalBooleanArg(arg2,"&");
    }
    public JamVal forOpOr() {
      BoolConstant b1 = evalBooleanArg(arg1,"|");
      if (b1 == BoolConstant.TRUE) return BoolConstant.TRUE;
      return evalBooleanArg(arg2,"|");
    }
    // public JamVal forOpGets(OpGets op) { return ... ; }  // Supports addition of ref cells to Jam
  }
    
  static private class StandardPrimFunVisitor implements PrimFunVisitor<JamVal> {
    
    JamVal[] vals;  // evaluated arguments for PrimFun App
    
    StandardPrimFunVisitor(JamVal[] vls) { vals = vls; }
    
    private JamVal primFunError(String fn) {
      throw new EvalException("Primitive function `" + fn + "' applied to " + vals.length + " arguments");
    }
    
    /** Confirms that vals[0] (first evaluated argument in application of fun) is a JamCons. Assumes that any
      * such function is unary. */
    private JamCons confirmJamCons(String funName) {
      if (vals[0] instanceof JamCons) return (JamCons) vals[0];
      throw new EvalException("Primitive function `" + funName + "' applied to argument " + vals[0] + " that is not a JamCons");
    }
    
    public JamVal forFunctionPPrim() {
      if (vals.length != 1) return primFunError("function?");
      return BoolConstant.toBoolConstant(vals[0] instanceof JamFun);
    }
    
    public JamVal forNumberPPrim() {
      if (vals.length != 1) return primFunError("number?");
      return BoolConstant.toBoolConstant(vals[0] instanceof IntConstant);
    }
    
    public JamVal forListPPrim() {
      if (vals.length != 1) return primFunError("list?");
      return BoolConstant.toBoolConstant(vals[0] instanceof JamList);
    }
    
    public JamVal forConsPPrim() {
      if (vals.length != 1) return primFunError("cons?");
      return BoolConstant.toBoolConstant(vals[0] instanceof JamCons);
    }
    
    public JamVal forEmptyPPrim() {
      if (vals.length != 1) return primFunError("empty?");
      return BoolConstant.toBoolConstant(vals[0] instanceof JamEmpty);
    }
    
    public JamVal forConsPrim() {
      if (vals.length != 2) return primFunError("cons");
      if (vals[1] instanceof JamList) return new JamCons(vals[0], (JamList) vals[1]);
      throw new EvalException("Second argument " + vals[1] + " to `cons' is not a JamList");
    }
    
    public JamVal forArityPrim() { 
      if (vals.length != 1) primFunError("arity");
      if (!(vals[0] instanceof JamFun))  throw new EvalException("arity applied to argument " +  vals[0]);
      
      return ((JamFun)vals[0]).accept(new JamFunVisitor<IntConstant>() {  // anonymous JamFunVisitor
        public IntConstant forJamClosure(JamClosure jc) { return new IntConstant(jc.body().vars().length); }
        public IntConstant forPrimFun(PrimFun jpf) { return new IntConstant(jpf instanceof ConsPrim ? 2 : 1); }
      });
    }
    
    public JamVal forFirstPrim() { return confirmJamCons("first").first(); }
    public JamVal forRestPrim() { return confirmJamCons("rest").rest(); }
    
  }
}
 
class EvalException extends RuntimeException {
  EvalException(String msg) { super(msg); }
}
