/*
 * UTEExpress - bo sung trang thai FAILED cho van don.
 *
 * Java da co OrderStatus.FAILED, nhung SQL Server van giu CHECK constraint
 * cu do Hibernate tao. Script nay xoa constraint cu va tao lai constraint
 * moi co them FAILED.
 *
 * Script co the chay lai nhieu lan.
 */

USE uteexpress;
GO

DECLARE @dropSql nvarchar(max) = N'';

SELECT @dropSql = @dropSql
        + N'ALTER TABLE dbo.shipment_order DROP CONSTRAINT '
        + QUOTENAME(cc.name)
        + N';'
FROM sys.check_constraints AS cc
WHERE cc.parent_object_id = OBJECT_ID(N'dbo.shipment_order')
  AND (
        cc.parent_column_id = COLUMNPROPERTY(
                OBJECT_ID(N'dbo.shipment_order'),
                N'status',
                'ColumnId')
        OR cc.definition LIKE N'%PENDING_PICKUP%'
      );

IF @dropSql <> N''
    EXEC sys.sp_executesql @dropSql;
GO

ALTER TABLE dbo.shipment_order
ADD CONSTRAINT CK_shipment_order_status
CHECK (
    status IN (
        'PENDING_PICKUP',
        'PICKED_UP',
        'IN_TRANSIT',
        'DELIVERED',
        'FAILED',
        'CANCELLED',
        'RETURNED'
    )
);
GO

SELECT
    name AS constraint_name,
    definition
FROM sys.check_constraints
WHERE parent_object_id = OBJECT_ID(N'dbo.shipment_order');
GO