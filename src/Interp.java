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
    /** Your code goes here ... */
    public String toString() { return "[" + var + ", " + value + "]"; }
  }
  
  /** Class representing a binding in CBName evaluation. The inherited value field is ignored. */
  static class NameBinding extends Binding {
    protected Suspension susp;
    /** Your code goes here ... */
    public String toString() { return "[" + var + ", " + susp + "]"; }
  }
  
  /** Class representing a binding in CBNeed evaluation.  The inherited value field is used to hold the value
    * first computed by need .. */
  static class NeedBinding extends NameBinding {
       /** Your code goes here ... */
    public String toString() { return "[" + var + ", " + value + ", " + susp + "]"; }
  }
  
  /** Helper method supporting Binding classes */
  static JamVal illegalForwardReference(Variable v) {
    throw new EvalException("Attempt to evaluate variable " + v + " bound to null, indicating an illegal forward reference");
  }
  
  /** Binding policy for call-by-value. */
  static final BindingPolicy CALL_BY_VALUE = new BindingPolicy() {
    public Binding newBinding(Variable var, AST arg, EvalVisitor ev) { /** Your code goes here ... */ }
    public Binding newDummyBinding(Variable var) { /** Your code goes here ... */ } 
  };
  
  /** Binding policy for call-by-name. */
  static final BindingPolicy CALL_BY_NAME = new BindingPolicy() {
    public Binding newBinding(Variable var, AST arg, EvalVisitor ev) { /** Your code goes here ... */ }
    public Binding newDummyBinding(Variable var) { /** Your code goes here ... */ }
  };
  
  /** Binding policy for call-by-need. */
  static final BindingPolicy CALL_BY_NEED = new BindingPolicy() {
    public Binding newBinding(Variable var, AST arg, EvalVisitor ev) { /** Your code goes here ... */ }
    public Binding newDummyBinding(Variable var) { /** Your code goes here ... */ }
  };
  
  /** A class representing an AST paired with the corresponding evaluator. */
  static class ConcreteSuspension implements Suspension {
    private AST exp;
    private EvalVisitor ev; 
    /** Your code goes here ... */
    public String toString() { return "<" + exp + ", " + ev + ">"; }
  }
  
  static class Trivial {}
  
  /** Class for a lazy cons structure. */
  static class JamLazyNameCons extends JamCons {
    /** Suspension for first */
    protected Suspension firstSusp;
    /** Suspension for rest */
    protected Suspension restSusp;
    
    /** Your code goes here ... */
  }
  
  /** Class for a lazy cons with optimization. */
  static class JamLazyNeedCons extends JamLazyNameCons {
    /** Your code goes here ... */
  }
  
  /** Eager cons evaluation policy. presume that args has exactly 2 elements. */
  public static final ConsPolicy EAGER = new ConsPolicy() {
    /** Your code goes here ... */
  };
  
  /** Call-by-name lazy cons evaluation policy. */
  public static final ConsPolicy LAZYNAME = new ConsPolicy() {
    public JamVal evalCons(AST[] args, EvalVisitor ev) { /** Your code goes here ... */ }
  };
  
  /** Call-by-need lazy cons evaluation policy. */
  public static final ConsPolicy LAZYNEED = new ConsPolicy() {
    public JamVal evalCons(AST[] args, EvalVisitor ev) { /** Your code goes here ... */ }
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
    
    /** Your code goes here ... */
    
    /* ASTVisitor<JamVal> methods */
    
    /** Your code goes here ... */
    
    /* Inner classes */
    
    /** Evaluates the application of a function to an array of argument ASTs. A true inner class that accesses the enclosing 
      * EvalVisitor instance. The applied function may be a JamClosure or a PrimFun*/
    class FunEvaluator implements JamFunVisitor<JamVal> {
     
      /** Your code goes here ... */
      
      /** The anonymous inner class that evaluates PrimFun applications.  The evaluation of arguments has been deferred
        * for the sake of lazy cons. As a result, the evalArgs() method is called in most of the forXXXX methods, adding
        * extra lines of code in comparison to Project 2. */
      PrimFunVisitor<JamVal> primEvaluator = new PrimFunVisitor<JamVal>() {
        /** Your code goes here ... */
      };  
      
      /* Support for JamFunVisitor<JamVal> interface */
      
      /** Your code goes here ... */
      
      /* Evaluates the primFun application.  The arguments cannot be evaluated yet because cons may be lazy. */
      public JamVal forPrimFun(PrimFun primFun) {  /** Your code goes here ... */ }
    }
    
    /** Evaluator for unary operators. Operand is already value (JamVal). */
    static class UnOpEvaluator implements UnOpVisitor<JamVal> {
      /** Your code goes here ... */
      
      /* Visitor methods */
      
      /** Your code goes here ... */
      
    } // end of BinOpEvaluator class
  } // end of EvalVisitor class
} // end of Interpreter class
