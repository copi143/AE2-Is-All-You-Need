package kaptor.ir

sealed interface IrType
data object IrIntType : IrType
data object IrLongType : IrType
data object IrFloatType : IrType
data object IrDoubleType : IrType
data object IrBoolType : IrType
data object IrStringType : IrType
data object IrObjectType : IrType
