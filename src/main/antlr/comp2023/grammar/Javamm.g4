grammar Javamm;

@header {
    package pt.up.fe.comp2023;
}

INT : ([0]|[1-9][0-9]*) ;
ID : [a-zA-Z_$][a-zA-Z_0-9$]* ;

LINE_COMMENT : '//' (~[\n]*)'\n' -> skip;
BLOCK_COMMENT : '/*' .*? '*/' -> skip;
WS : [ \t\n\r\f]+ -> skip ;

program
    : (importDeclaration)* classDeclaration EOF
    ;

importDeclaration
    : 'import' subImport ( '.' subImport )* ';'
    ;

subImport
    : subImportName = ID
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
    | (accessModifier='public')? isStatic='static' 'void' methodName='main' '(' argumentType='String[]' argumentName=ID ')' '{' ( varDeclaration )* ( statement )* '}'
    ;

typeRet
    : type #ReturnType
    | 'void' #ReturnType
    ;

typeDecl
    : type #DeclarationType
    ;

type
    : varType = 'int[]'
    | varType='boolean'
    | varType='int'
    | varType=ID
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
    | variable = ID '=' expression ';' #Assignment
    | variable = ID '=' ('new' type)? '{' (contents+=INT',')*contents+=INT '}' ';' #AssignmentArray
    | variable = ID '=' 'new' 'int' '[' arraySize=INT ']' ';' #ArrayDeclaration
    | id=ID '[' expression ']' '=' expression ';' #Assignment
    | expression ';' #Stmt
    ;

expression
    : ('(' expression ')' | '[' expression ']') #Parenthesis
    | expression '[' expression ']' #ArrayIndex
    | expression '.' method = ID '(' ( expression ( ',' expression )* )? ')' #MethodCall
    | expression '.' method='length' #Length
    | expression '.' method = ID #ClassVariable
    | op='!' expression #UnaryOp
    | expression op=('*' | '/' ) expression #BinaryOp
    | expression op=('+' | '-' ) expression #BinaryOp
    | expression op=('<' | '>' | '<=' | '>=' | '!=' | '==' ) expression #BinaryOp
    | expression op='&&' expression #BinaryOp
    | expression op='||' expression  #BinaryOp
    | value=expression op='?' value=expression op=':' value=expression  #TernaryOp
    | 'new' objectName = ID '(' ')' #ObjectInstantiation
    | integer=INT #Literal
    | bool='true' #Literal
    | bool='false' #Literal
    | id=ID #LiteralS
    | id='this' #Object
    ;

