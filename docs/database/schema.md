# Schema contract

Core entities: User, Permission, Category, Product, Customer, Supplier, Sale/SaleItem, Purchase/PurchaseItem, Expense, StockMovement, AppSettings, SyncQueue, BackupMetadata. Each synchronizable entity will use UUID identity, timestamps, and sync metadata. Inventory is movement-ledger based; only PURCHASE and SALE are approved active movement causes.

