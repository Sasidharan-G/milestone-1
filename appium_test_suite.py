import unittest
import time
import subprocess
import sqlite3
import os
from appium import webdriver
from appium.webdriver.common.appiumby import AppiumBy
from appium.options.android import UiAutomator2Options
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

DB_PULL_CMD = [
    "adb", "shell", "run-as", "com.company.billing",
    "sh", "-c", "cat databases/billing.db > /sdcard/billing.db; cat databases/billing.db-wal > /sdcard/billing.db-wal; cat databases/billing.db-shm > /sdcard/billing.db-shm"
]

class DBHelper:
    @staticmethod
    def pull_db():
        # Force checkpoint or just pull all 3 files
        subprocess.run(DB_PULL_CMD, capture_output=True)
        subprocess.run(["adb", "pull", "/sdcard/billing.db", "billing.db"], capture_output=True)
        subprocess.run(["adb", "pull", "/sdcard/billing.db-wal", "billing.db-wal"], capture_output=True)
        subprocess.run(["adb", "pull", "/sdcard/billing.db-shm", "billing.db-shm"], capture_output=True)

    @staticmethod
    def query_single(sql, params=()):
        DBHelper.pull_db()
        conn = sqlite3.connect("billing.db")
        cur = conn.cursor()
        cur.execute(sql, params)
        res = cur.fetchone()
        conn.close()
        return res

class CompleteBillingAppTests(unittest.TestCase):
    
    @classmethod
    def setUpClass(cls):
        options = UiAutomator2Options()
        options.platform_name = "Android"
        options.device_name = "Android Emulator"
        options.app = "C:/Users/SasiDharan G/OneDrive/Desktop/milestone-1/android-app/app/build/outputs/apk/debug/app-debug.apk"
        options.app_package = "com.company.billing"
        options.app_activity = ".MainActivity"
        options.no_reset = False # Fresh install every time
        options.new_command_timeout = 300
        
        cls.driver = webdriver.Remote("http://127.0.0.1:4723", options=options)
        cls.driver.implicitly_wait(5)
        cls.wait = WebDriverWait(cls.driver, 20)

    @classmethod
    def tearDownClass(cls):
        cls.driver.quit()

    def setUp(self):
        # Implicitly wait reset
        self.driver.implicitly_wait(5)

    def tearDown(self):
        print("Test Complete. Pausing and returning to Dashboard...")
        time.sleep(2)
        # Attempt to return to Dashboard if stuck
        for _ in range(3):
            self.driver.implicitly_wait(0)
            dashboard = self.driver.find_elements(AppiumBy.XPATH, "//*[contains(@text, 'Dashboard') or contains(@text, 'KadaKutty') or contains(@text, 'My Shop')]")
            if dashboard:
                self.driver.implicitly_wait(5)
                break
            
            login = self.driver.find_elements(AppiumBy.XPATH, "//*[contains(@text, 'Sign In') or contains(@content-desc, 'Sign In')]")
            if login:
                self.driver.implicitly_wait(5)
                break
            
            self.driver.press_keycode(4) # Back button
            time.sleep(1)
        self.driver.implicitly_wait(5)

    # --- UI Helpers ---
    def click_text(self, text, exact=False):
        xpath = f"//*[@text='{text}']" if exact else f"//*[contains(@text, '{text}') or contains(@content-desc, '{text}')]"
        el = self.wait.until(EC.presence_of_element_located((AppiumBy.XPATH, xpath)))
        el.click()
        time.sleep(1) # Compose animation

    def type_in_field(self, label_text, value, exact=False):
        xpath = f"//android.widget.EditText[@text='{label_text}']" if exact else f"//android.widget.EditText[contains(@text, '{label_text}')]"
        el = self.wait.until(EC.presence_of_element_located((AppiumBy.XPATH, xpath)))
        el.click()
        el.clear()
        el.send_keys(value)
        self.driver.hide_keyboard()
        time.sleep(0.5)

    def login_helper(self):
        time.sleep(4)
        self.driver.implicitly_wait(0)
        dash = self.driver.find_elements(AppiumBy.XPATH, "//*[contains(@text, 'Dashboard') or contains(@text, 'KadaKutty') or contains(@text, 'My Shop')]")
        self.driver.implicitly_wait(5)
        if dash:
            print("Already logged in.")
            return

        print("Logging in...")
        inputs = self.wait.until(EC.presence_of_all_elements_located((AppiumBy.CLASS_NAME, "android.widget.EditText")))
        if len(inputs) >= 2:
            inputs[0].clear()
            inputs[0].send_keys("sasidharangr9487@gmail.com")
            inputs[1].clear()
            inputs[1].send_keys("123456")
        
        self.click_text("Sign In", exact=True)
        self.wait.until(EC.presence_of_element_located((AppiumBy.XPATH, "//*[contains(@text, 'Dashboard') or contains(@text, 'My Shop')]")))
        time.sleep(2)

    # --- Tests ---
    def test_01_login(self):
        print("\n[TEST] 1. Authentication")
        self.login_helper()

    def test_02_master_category_product(self):
        print("\n[TEST] 2. Category & Product Masters")
        self.login_helper()
        self.click_text("Master Data")
        
        # Category
        self.click_text("Categories")
        self.click_text("Add") # Assuming Add button or FAB
        self.type_in_field("Name", "Beverages")
        self.click_text("Save")
        
        self.driver.press_keycode(4) # Back to Master Data
        time.sleep(1)

        # Product
        self.click_text("Products")
        self.click_text("Add")
        self.type_in_field("Name", "Cola 1L")
        
        # Category dropdown
        self.click_text("Category")
        self.click_text("Beverages", exact=True)
        
        self.type_in_field("Purchase Price", "35.00")
        self.type_in_field("Sale Price", "50.00")
        self.click_text("Save")

        # Database Verification
        print("Verifying Product in SQLite DB...")
        row = DBHelper.query_single("SELECT name, purchasePriceMinorUnits, salePriceMinorUnits FROM products WHERE name='Cola 1L'")
        self.assertIsNotNone(row, "Product not found in DB")
        self.assertEqual(row[0], "Cola 1L")
        self.assertEqual(row[1], 3500) # Minor units (e.g. paisa/cents)
        self.assertEqual(row[2], 5000)
        print("Product DB Verification Passed!")

    def test_03_customer_supplier(self):
        print("\n[TEST] 3. Customer & Supplier Masters")
        self.login_helper()
        self.click_text("Master Data")
        
        # Customer
        self.click_text("Customers")
        self.click_text("Add")
        self.type_in_field("Name", "VIP Customer")
        self.type_in_field("Phone", "9876543210")
        self.click_text("Save")
        
        self.driver.press_keycode(4)
        time.sleep(1)

        # Supplier
        self.click_text("Suppliers")
        self.click_text("Add")
        self.type_in_field("Name", "Cola Distributor")
        self.click_text("Save")

        # Database Verification
        print("Verifying Customer & Supplier in DB...")
        cust = DBHelper.query_single("SELECT name, phone FROM customers WHERE name='VIP Customer'")
        self.assertEqual(cust[1], "9876543210")
        sup = DBHelper.query_single("SELECT name FROM suppliers WHERE name='Cola Distributor'")
        self.assertIsNotNone(sup)
        print("Customer & Supplier DB Verification Passed!")

    def test_04_purchases(self):
        print("\n[TEST] 4. Purchase Entry (Stock Update)")
        self.login_helper()
        self.click_text("Purchases & Stock")
        
        self.click_text("Add Purchase")
        
        # Supplier
        self.click_text("Supplier")
        self.click_text("Cola Distributor", exact=True)
        
        # Add item (Assuming UI flow)
        try:
            self.click_text("Add Item")
        except:
            pass # Maybe not needed
            
        self.click_text("Product")
        self.click_text("Cola 1L", exact=True)
        
        self.type_in_field("Qty", "100")
        self.click_text("Save")
        
        # DB Verification
        print("Verifying Stock Movement in DB...")
        row = DBHelper.query_single("SELECT SUM(quantityDelta) FROM stock_movements INNER JOIN products ON stock_movements.productId = products.id WHERE products.name = 'Cola 1L'")
        self.assertIsNotNone(row)
        self.assertGreaterEqual(row[0], 100)
        print("Stock Verification Passed!")

    def test_05_sales_billing(self):
        print("\n[TEST] 5. Sales Invoicing & Billing Logic")
        self.login_helper()
        self.click_text("Sales Invoicing")
        
        # Customer
        self.click_text("Customer")
        self.click_text("VIP Customer", exact=True)
        
        # Product
        self.click_text("Product")
        self.click_text("Cola 1L", exact=True)
        
        self.type_in_field("Qty", "5")
        
        try:
            self.click_text("Add to Invoice")
        except:
            pass
            
        # Verify UI Total (5 * 50.00 = 250.00)
        total_text = self.driver.find_element(AppiumBy.XPATH, "//*[contains(@text, '250.00')]").text
        self.assertIn("250.00", total_text)
        
        self.click_text("Generate & Save Bill")
        
        # DB Verification
        print("Verifying Sale & Totals in DB...")
        sale = DBHelper.query_single("SELECT id, totalMinorUnits FROM sales ORDER BY createdAtEpochMs DESC LIMIT 1")
        self.assertIsNotNone(sale)
        self.assertEqual(sale[1], 25000) # 250.00 in minor units
        
        sale_item = DBHelper.query_single("SELECT quantity, unitPriceMinorUnits, lineTotalMinorUnits FROM sale_items WHERE saleId=?", (sale[0],))
        self.assertEqual(sale_item[0], 5)
        self.assertEqual(sale_item[1], 5000)
        self.assertEqual(sale_item[2], 25000)
        print("Sale Billing Logic DB Verification Passed!")

    def test_06_reports(self):
        print("\n[TEST] 6. Reports Engine")
        self.login_helper()
        self.click_text("Reports Engine")
        
        reports = ["Sale Amount", "Stock Report"]
        for report in reports:
            self.click_text(report)
            time.sleep(2)
            self.driver.press_keycode(4)
            time.sleep(1)

if __name__ == '__main__':
    unittest.main()
