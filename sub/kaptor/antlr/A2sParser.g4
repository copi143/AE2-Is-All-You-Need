parser grammar A2sParser;

options { tokenVocab = A2sLexer; }

@header { package kaptor.a2s.parser; }

// ── 脚本顶层 ──
script: topLevelDeclaration* EOF;

topLevelDeclaration
    : valDecl
    | varDecl
    | funDecl
    | eventDecl
    | eventHandler
    ;

// ── 声明 ──
valDecl: VAL Identifier (COLON type)? ASSIGN expression;
varDecl: VAR Identifier (COLON type)? ASSIGN expression;

funDecl
    : FUN Identifier LPAREN funParams? RPAREN (COLON type)? funBody
    ;
funParams: funParam (COMMA funParam)*;
funParam: Identifier COLON type;
funBody: ASSIGN expression | block;

eventDecl
    : EVENT Identifier LPAREN eventParams? RPAREN LBRACE funDecl* RBRACE
    ;
eventParams: eventParam (COMMA eventParam)*;
eventParam: VAL Identifier COLON type;

// 事件处理器：on/before/after + 事件名 + lambda（参数必须显式起名，无隐式 it）
eventHandler
    : hookType Identifier lambda
    ;
hookType: ON | BEFORE | AFTER;

// ── 语句 ──
statement
    : valDecl
    | varDecl
    | assignment
    | postStatement
    | exprStatement
    | forStatement
    | whileStatement
    | returnStatement
    | breakStatement
    | continueStatement
    | throwStatement
    | tryStatement
    ;

block: LBRACE statement* RBRACE;

assignment: expression assignOp expression;
assignOp: ASSIGN | PLUS_ASSIGN | MINUS_ASSIGN | STAR_ASSIGN | SLASH_ASSIGN | PERCENT_ASSIGN;

postStatement: POST Identifier callSuffix;
exprStatement: expression;

forStatement: FOR LPAREN Identifier IN expression RPAREN block;
whileStatement: WHILE LPAREN expression RPAREN block;

returnStatement: RETURN expression?;
breakStatement: BREAK;
continueStatement: CONTINUE;
throwStatement: THROW expression;

tryStatement: TRY block catchBlock* finallyBlock?;
catchBlock: CATCH LPAREN Identifier RPAREN block;
finallyBlock: FINALLY block;

// ── 类型 ──
type: baseType QUEST?;
baseType
    : primitiveType
    | BIGINT
    | RATIONAL
    | STACK
    | LIST LT type GT
    | ANY
    | UNIT
    | Identifier
    ;
primitiveType: I32 | I64 | U32 | U64 | F32 | F64 | BOOLEAN | STRING;

// ── 表达式（优先级从低到高，直接左递归） ──
expression: elvis;

elvis: disjunction (ELVIS elvis)?;
disjunction: disjunction OR conjunction | conjunction;
conjunction: conjunction AND equality | equality;
equality: equality (EQ | NEQ) comparison | comparison;
comparison: comparison (LT | GT | LE | GE) range | range;
range: range RANGE additive | additive;
additive: additive (PLUS | MINUS) multiplicative | multiplicative;
multiplicative: multiplicative (STAR | SLASH | PERCENT) unary | unary;
unary: (MINUS | NOT) unary | postfix;

postfix: primary postfixSuffix*;
postfixSuffix
    : DOT Identifier
    | SAFE_DOT Identifier
    | callSuffix
    | indexingSuffix
    | NOT_NULL
    | INCR
    | DECR
    ;

callSuffix: LPAREN expressionList? RPAREN;
indexingSuffix: LBRACKET expression RBRACKET;
expressionList: expression (COMMA expression)*;

// ── 基础表达式 ──
primary
    : literal
    | Identifier
    | RESOURCE_REF
    | LPAREN expression RPAREN
    | lambda
    | ifExpression
    | whenExpression
    ;

literal
    : IntegerLiteral
    | RealLiteral
    | TRUE
    | FALSE
    | NULL
    | stringLiteral
    ;

stringLiteral: QUOTE_OPEN stringPart* QUOTE_CLOSE;
stringPart
    : LineStrText
    | LineStrEscapedChar
    | LineStrEscapedDollar
    | LineStrRef
    | LineStrExprStart expression RBRACE
    ;

// ── lambda（参数必须显式起名，无隐式 it） ──
// 形式：{ e -> ... }、{ e, f -> ... }、{ -> ... }
lambda: LBRACE lambdaParams? ARROW statement* RBRACE;
lambdaParams: lambdaParam (COMMA lambdaParam)*;
lambdaParam: Identifier (COLON type)?;

// ── if / when 表达式 ──
ifExpression
    : IF LPAREN expression RPAREN controlBody (ELSE controlBody)?
    ;
controlBody: block | statement;

whenExpression
    : WHEN LPAREN expression RPAREN LBRACE whenEntry* RBRACE
    ;
whenEntry
    : expression (COMMA expression)* ARROW controlBody
    | ELSE ARROW controlBody
    ;
