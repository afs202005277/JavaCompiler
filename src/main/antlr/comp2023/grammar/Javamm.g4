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
    ;

methodDeclaration
    : (accessModifier='public')? returnType=type methodName = ID '(' ( argumentType=type argumentName=ID ( ',' argumentType=type argumentName=ID )* )? ')' '{' ( varDeclaration )* ( statement )* 'return' expression ';' '}'
    | (accessModifier='public')? isStatic='static' mainReturnType='void' name='main' '(' mainArgumentType='String' '[' ']' argumentName=ID ')' '{' ( varDeclaration )* ( statement )* '}'
    ;
type
    : 'int' '[' ']' #IntegerArray
    | 'boolean' #Boolean
    | 'int' #Integer
    | 'String' #String// extra
    | ID #Variable
    ;
elseStmt
    : 'else' statement #elseStmtBody
    ;
statement
    : '{' ( statement )* '}' #Body
    | 'if' '(' expression ')' statement elseStmt? #IfStmt
    | 'while' '(' expression ')' statement #WhileLoop
    | expression ';' #Stmt
    | variable = ID '=' expression ';' #Assignment
    | ID '[' expression ']' '=' expression ';' #Assignment
    ;

expression
    : '(' expression ')' #PriorityOp
    | expression '[' expression ']' #ArrayIndex
    | '!' expression #UnaryOp
    | value=expression op=( '*' | '/' ) value=expression #BinaryOp
    | value=expression op=('+' | '-' ) value=expression #BinaryOp
    | value=expression op='<' value=expression #BinaryOp
    | value=expression op='&&' value=expression #BinaryOp
    | expression '.' 'length' #Length
    | expression '.' ID '(' ( expression ( ',' expression )* )? ')' #MethodChaining
    | 'new' 'int' '[' expression ']' #IntArray
    | 'new' ID '(' ')' #ObjectInstantiation
    | integer=INTEGER #Literal
    | bool='true' #Literal
    | bool='false' #Literal
    | id=ID #Literal
    | id='this' #Literal
    ;

