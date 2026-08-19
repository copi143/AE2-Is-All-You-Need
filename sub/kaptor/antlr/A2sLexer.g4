lexer grammar A2sLexer;

@header { package kaptor.a2s.parser; }

// ── 空白与注释 ──
WS: [ \t\r\n]+ -> channel(HIDDEN);
LineComment: '//' ~[\r\n]* -> channel(HIDDEN);
BlockComment: '/*' .*? '*/' -> channel(HIDDEN);

// ── 关键字 ──
VAL: 'val';
VAR: 'var';
FUN: 'fun';
EVENT: 'event';
ON: 'on';
BEFORE: 'before';
AFTER: 'after';
POST: 'post';
IF: 'if';
ELSE: 'else';
WHEN: 'when';
FOR: 'for';
WHILE: 'while';
RETURN: 'return';
BREAK: 'break';
CONTINUE: 'continue';
TRY: 'try';
CATCH: 'catch';
FINALLY: 'finally';
THROW: 'throw';
IN: 'in';
TRUE: 'true';
FALSE: 'false';
NULL: 'null';

// ── 类型关键字（Rust 风格） ──
I32: 'i32';
I64: 'i64';
U32: 'u32';
U64: 'u64';
F32: 'f32';
F64: 'f64';
BOOLEAN: 'Boolean';
STRING: 'String';
BIGINT: 'BigInt';
RATIONAL: 'Rational';
STACK: 'Stack';
LIST: 'List';
ANY: 'Any';
UNIT: 'Unit';

// ── 字面量 ──

// 资源引用（反引号）：`diamond`、`minecraft:diamond`、`item|minecraft:diamond`
RESOURCE_REF: '`' ~[`\r\n]+ '`';

// 整数：默认 BigInt，后缀 _i32/_i64/_u32/_u64
// 千分位分隔符：_ 后跟数字；类型后缀：_ 后跟字母
IntegerLiteral: Digits IntSuffix?;

// 小数：默认 Rational，后缀 _f32/_f64
RealLiteral: Digits '.' Digits RealSuffix?;

fragment Digits: Digit (Digit | '_' Digit)*;
fragment Digit: [0-9];
fragment IntSuffix: '_' [iu] ('32' | '64');
fragment RealSuffix: '_' 'f' ('32' | '64');

// ── 标识符 ──
Identifier: Letter (Letter | Digit | '_')*;
fragment Letter: [a-zA-Z_];

// ── 字符串（进入 LineString 模式） ──
QUOTE_OPEN: '"' -> pushMode(LineString);

// ── 运算符与分隔符 ──
// 大括号用 push/pop 管理模式栈，使字符串模板 ${...} 内的 { } 能正确配对
LBRACE: '{' -> pushMode(DEFAULT_MODE);
RBRACE: '}' { if (!_modeStack.isEmpty()) popMode(); };
LPAREN: '(';
RPAREN: ')';
LBRACKET: '[';
RBRACKET: ']';
COMMA: ',';
DOT: '.';
SAFE_DOT: '?.';
COLON: ':';
ARROW: '->';
ELVIS: '?:';
NOT_NULL: '!!';
ASSIGN: '=';
PLUS: '+';
MINUS: '-';
STAR: '*';
SLASH: '/';
PERCENT: '%';
PLUS_ASSIGN: '+=';
MINUS_ASSIGN: '-=';
STAR_ASSIGN: '*=';
SLASH_ASSIGN: '/=';
PERCENT_ASSIGN: '%=';
EQ: '==';
NEQ: '!=';
LT: '<';
GT: '>';
LE: '<=';
GE: '>=';
AND: '&&';
OR: '||';
NOT: '!';
INCR: '++';
DECR: '--';
RANGE: '..';
QUEST: '?';

// ── 字符串模式 ──
mode LineString;

QUOTE_CLOSE: '"' -> popMode;
LineStrText: ~('\\' | '"' | '$')+ | '$';
LineStrEscapedChar: '\\' (['"\\nt$] | 'u' HexDigit HexDigit HexDigit HexDigit);
LineStrEscapedDollar: '$$';
LineStrExprStart: '${' -> pushMode(DEFAULT_MODE);
LineStrRef: '$' Identifier;

fragment HexDigit: [0-9a-fA-F];
