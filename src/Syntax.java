/** A visitor class for the syntax checker. Returns normally unless there is a syntax error. On a syntax error, 
  * throws a SyntaxException.
  */
class CheckVisitor implements ASTVisitor<Void> {
  /** Empty symbol table. */
  private static final Empty<Variable> EMPTY_VARS = new Empty<Variable>();
  
  /** Symbol table to detect free variables. */
  PureList<Variable> env;
  
  /** Root form of CheckVisitor. */
  public static final CheckVisitor INITIAL = new CheckVisitor(EMPTY_VARS);

  CheckVisitor(PureList<Variable> e) { env = e; }
  
  /** Helper method that converts an array to a PureList. */
  public static <T> PureList<T> arrayToList(T[] array) {
    /** Your code goes here ... */
  }
  
  /*  Visitor methods. */
  
  /** Your code goes here ... */
}

/** Singleton visitor that checks for duplicate variables in a symbol table. Returns normally unless an error is found.
  * Throws a SyntaxException on error.
  */
class AnyDuplicatesVisitor implements PureListVisitor<Variable, Void> {
  /** Your code goes here */
}

/** Exception type thrown by the context-sensitive checker. */
class SyntaxException extends RuntimeException {
  SyntaxException(String s) { super(s); }
}
