grammar Javamm;

@header {
    package pt.up.fe.comp2023;
}

INTEGER : [0-9]+ ;
ID : [a-zA-Z_][a-zA-Z_0-9]* ;

WS : [ \t\n\r\f]+ -> skip ;

program
    : (importDeclaration)* classDeclaration EOF
    ;

importDeclaration
    : 'import' importedClass = ID ( '.' ID )* ';'
    ;

classDeclaration
    : 'class' className = ID ('extends' extendedClassName = ID)? '{' ( varDeclaration )* ( methodDeclaration )* '}'
    ;

varDeclaration
    : type variableName = ID ';'
    | type statement
    ;

returnStmt
    : 'return' expression
    ;

argument
    : typeDecl argumentName=ID #MethodArgument
    ;

methodDeclaration
    : (accessModifier='public')? typeRet methodName = ID '(' ( argument ( ',' argument )* )? ')' '{' ( varDeclaration )* ( statement )* returnStmt ';' '}'
    | (accessModifier='public')? isStatic='static' 'void' methodName='main' '(' 'String[]' argumentName=ID ')' '{' ( varDeclaration )* ( statement )* '}'
    ;

typeRet
    : type #ReturnType
    ;

typeDecl
    : type #DeclarationType
    ;

type
    : 'int' '[' ']' #IntegerArray
    | 'boolean' #Boolean
    | 'int' #Integer
    | 'String' #String// extra
    | ID #VariableID
    ;

elseStmt
    : 'else' statement #elseStmtBody
    ;

condition : expression;
statement
    : '{' ( statement )* '}' #Body
    | 'if' '(' condition ')' statement elseStmt? #IfStatement
    | 'while' '(' expression ')' statement #WhileLoop
    | 'for' '(' (varDeclaration | expression ';') expression ';' expression ')' statement #ForLoop
    | expression ';' #Stmt
    | variable = ID '=' expression ';' #Assignment
    | ID '[' expression ']' '=' expression ';' #Assignment
    ;

expression
    : ('(' expression ')' | '[' expression ']') #Parenthesis
    | expression '[' expression ']' #ArrayIndex
    | ('++' | '--' | '+' | '-' | '!' | '~' | '(' type ')') expression #UnaryOp
    | value=expression op=('*' | '/' ) value=expression #BinaryOp
    | value=expression op=('+' | '-' ) value=expression #BinaryOp
    | expression ('<<' | '>>' | '>>>') #BinaryOp
    | expression ('<' | '>' | '<=' | '>=' | '!=' ) expression #BinaryOp
    | expression '&' expression #BinaryOp
    | expression '^' expression #BinaryOp
    | expression '|' expression #BinaryOp
    | value=expression op='&&' value=expression #BinaryOp
    | expression '||' expression  #BinaryOp
    | expression '?' expression ':' expression  #TernaryOp
    | expression '.' 'length' #Length
    | expression '.' method = ID '(' ( expression ( ',' expression )* )? ')' #MethodCall
    | 'new' 'int' '[' expression ']' #IntArray
    | 'new' objectName = ID '(' ')' #ObjectInstantiation
    | integer=INTEGER #Literal
    | bool='true' #Literal
    | bool='false' #Literal
    | id=ID #Literal
    | id='this' #Literal
    ;

