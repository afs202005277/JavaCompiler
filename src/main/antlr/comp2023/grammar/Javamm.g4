grammar Javamm;

@header {
    package pt.up.fe.comp2023;
}

INTEGER : [0-9]+ ;
ID : [a-zA-Z_][a-zA-Z_0-9]* ;
//COMMENT : //.*|/\*(.|\n)*\*/ -> skip ;

WS : [ \t\n\r\f]+ -> skip ;

program
    : (importDeclaration)* classDeclaration EOF
    ;

importDeclaration
    : 'import' ID ( '.' ID )* ';'
    ;

classDeclaration
    : 'class' ID ('extends' ID)? '{' ( varDeclaration )* ( methodDeclaration )* '}'
    ;

varDeclaration
    : type ID ';'
    | type statement ';'
    ;

methodDeclaration
    : ('public')? type ID '(' ( type ID ( ',' type ID )* )? ')' '{' ( varDeclaration )* ( statement )* 'return' expression ';' '}'
    | ('public')? 'static' 'void' 'main' '(' 'String' '[' ']' ID ')' '{' ( varDeclaration )* ( statement )* '}'
    ;
type
    : 'int' '[' ']'
    | 'boolean'
    | 'int'
    | 'String' // extra
    | ID
    ;
statement
    : '{' ( statement )* '}'
    | 'if' '(' expression ')' statement 'else' statement
    | 'while' '(' expression ')' statement
    | 'for' '(' (varDeclaration | expression ';') expression ';' expression ')' statement
    | expression ';'
    | ID '=' expression ';'
    | ID '[' expression ']' '=' expression ';'
    ;

expression
    : ('(' expression ')' | '[' expression ']')
    | expression '[' expression ']'
    | ('++' | '--' | '+' | '-' | '!' | '~' | '(' type ')') expression
    | expression ('++' | '--')
    | expression ( '*' | '/' | '%' ) expression
    | expression ('+' | '-' ) expression
    | expression ('<<' | '>>' | '>>>')
    | expression ('<' | '>' | '<=' | '>=' | '!=' ) expression
    | 'instanceof' ID
    | expression '&' expression
    | expression '^' expression
    | expression '|' expression
    | expression '&&' expression
    | expression '||' expression
    | expression '?' expression ':' expression
    | expression '.' 'length'
    | expression '.' ID '(' ( expression ( ',' expression )* )? ')'
    | 'new' 'int' '[' expression ']'
    | 'new' ID '(' ')'
    | INTEGER
    | 'true'
    | 'false'
    | ID
    | 'this'
    ;

/*
Original
expression
    : expression ('&&' | '<' | '+' | '-' | '*' | '/' ) expression
    | expression '[' expression ']'
    | expression '.' 'length'
    | expression '.' ID '(' ( expression ( ',' expression )* )? ')'
    | 'new' 'int' '[' expression ']'
    | 'new' ID '(' ')'
    | '!' expression
    | '(' expression ')'
    | INTEGER
    | 'true'
    | 'false'
    | ID
    | 'this'
    ;*/
