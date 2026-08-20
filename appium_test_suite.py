import unittest
from appium import webdriver
from appium.webdriver.common.appiumby import AppiumBy
from appium.options.android import UiAutomator2Options
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
import time

class CompleteBillingAppTests(unittest.TestCase):
    
    @classmethod
    def setUpClass(cls):
        options = UiAutomator2Options()
        options.platform_name = "Android"
        options.device_name = "Android Emulator"
        options.app = "C:/Users/SasiDharan G/OneDrive/Desktop/milestone-1/android-app/app/build/outputs/apk/debug/app-debug.apk"
        options.app_package = "com.company.billing"
        options.app_activity = ".MainActivity"
        options.no_reset = True # Do not clear app data, use existing session
        
        cls.driver = webdriver.Remote("http://127.0.0.1:4723", options=options)
        cls.driver.implicitly_wait(5)
        cls.wait = WebDriverWait(cls.driver, 40)

    @classmethod
    def tearDownClass(cls):
        cls.driver.quit()

    def tearDown(self):
        print("Module complete. Pausing and returning to Dashboard...")
        time.sleep(3) # Give user time to see what happened
        for _ in range(4):
            try:
                self.driver.find_element(AppiumBy.XPATH, "//*[contains(@text, 'Dashboard') or contains(@text, 'KadaKutty')]")
                break
            except:
                try:
                    self.driver.find_element(AppiumBy.XPATH, "//*[contains(@text, 'Sign In') or contains(@content-desc, 'Sign In')]")
                    break # On login screen, we shouldn't press back further
                except:
                    self.driver.press_keycode(4) # Back button
                    time.sleep(1)
        time.sleep(1)

    def login_helper(self):
        # Wait for the screen to render
        time.sleep(4)
        
        # Check if already logged in (Dashboard visible)
        try:
            self.driver.find_element(AppiumBy.XPATH, "//*[contains(@text, 'Dashboard') or contains(@text, 'KadaKutty')]")
            print("Already logged in. Skipping login step.")
            return
        except:
            pass # Not on dashboard, proceed with login
            
        # In Compose, finding OutlinedTextField by label text can fail.  
        # Safest way is to find all EditTexts and use index (0 = Email, 1 = Password)
        inputs = self.wait.until(EC.presence_of_all_elements_located((AppiumBy.CLASS_NAME, "android.widget.EditText")))
        username_field = inputs[0]
        password_field = inputs[1]
        
        # Click login button (Jetpack compose button has text 'Sign In')
        login_btn = self.driver.find_element(AppiumBy.XPATH, "//*[contains(@text, 'Sign In') or contains(@content-desc, 'Sign In')]")
        
        username_field.clear()
        username_field.send_keys("sasidharangr9487@gmail.com")
        password_field.clear()
        password_field.send_keys("123456")
        login_btn.click()
        
        # Wait for Dashboard
        self.wait.until(EC.presence_of_element_located((AppiumBy.XPATH, "//*[contains(@text, 'Dashboard') or contains(@text, 'KadaKutty')]")))
        time.sleep(1) # Compose animation settle time

    def test_01_login_validation(self):
        print("Testing: Login Fields & Validation...")
        time.sleep(4)
        
        # Ensure we are either on Dashboard or Login
        for _ in range(5):
            try:
                self.driver.find_element(AppiumBy.XPATH, "//*[contains(@text, 'Dashboard') or contains(@text, 'KadaKutty')]")
                print("Already on Dashboard. Skipping login validation test.")
                return
            except:
                try:
                    self.driver.find_element(AppiumBy.XPATH, "//*[contains(@text, 'Sign In') or contains(@content-desc, 'Sign In')]")
                    break # On login screen, we can proceed
                except:
                    # Neither on Dashboard nor Login, probably stuck on a sub-screen from a previous run
                    self.driver.press_keycode(4) # Press Back
                    time.sleep(1)
            
        login_btn = self.wait.until(EC.presence_of_element_located((AppiumBy.XPATH, "//*[contains(@text, 'Sign In') or contains(@content-desc, 'Sign In')]")))
        login_btn.click() # Click empty to test validation errors
        time.sleep(1)
        self.login_helper()

    def test_02_masters_category(self):
        print("Testing: Category Master...")
        self.login_helper()
        self.driver.find_element(AppiumBy.XPATH, "//*[@text='Master Data']").click()
        time.sleep(1)
        self.wait.until(EC.presence_of_element_located((AppiumBy.XPATH, "//*[@text='Categories']"))).click()
        
        self.wait.until(EC.presence_of_element_located((AppiumBy.XPATH, "//*[@text='Add' or contains(@content-desc, 'Add')]"))).click()
        time.sleep(1)
        
        self.wait.until(EC.presence_of_element_located((AppiumBy.XPATH, "//android.widget.EditText[contains(@text, 'Name')]"))).send_keys("Groceries")
        self.driver.find_element(AppiumBy.XPATH, "//*[@text='Save']").click()
        time.sleep(1)
        
    def test_03_masters_product(self):
        print("Testing: Product Master...")
        self.login_helper()
        self.driver.find_element(AppiumBy.XPATH, "//*[@text='Master Data']").click()
        time.sleep(1)
        self.wait.until(EC.presence_of_element_located((AppiumBy.XPATH, "//*[@text='Products']"))).click()
        
        self.wait.until(EC.presence_of_element_located((AppiumBy.XPATH, "//*[@text='Add' or contains(@content-desc, 'Add')]"))).click()
        time.sleep(1)
        
        self.wait.until(EC.presence_of_element_located((AppiumBy.XPATH, "//android.widget.EditText[contains(@text, 'Name')]"))).send_keys("Sugar 1KG")
        
        # Category Dropdown Selection (Fixing logical dependency)
        self.driver.find_element(AppiumBy.XPATH, "//android.widget.EditText[contains(@text, 'Category')]").click()
        time.sleep(1.5) # Wait for dropdown animation
        self.driver.find_element(AppiumBy.XPATH, "//*[@text='Groceries']").click()
        
        self.driver.find_element(AppiumBy.XPATH, "//android.widget.EditText[contains(@text, 'Purchase Price')]").send_keys("40.00")
        self.driver.find_element(AppiumBy.XPATH, "//android.widget.EditText[contains(@text, 'Sale Price')]").send_keys("45.00")
        self.driver.find_element(AppiumBy.XPATH, "//*[@text='Save']").click()
        time.sleep(1)

    def test_04_masters_customer_and_supplier(self):
        print("Testing: Customer & Supplier Masters...")
        self.login_helper()
        self.driver.find_element(AppiumBy.XPATH, "//*[@text='Master Data']").click()
        time.sleep(1)
        
        # Customer
        self.wait.until(EC.presence_of_element_located((AppiumBy.XPATH, "//*[@text='Customers']"))).click()
        self.wait.until(EC.presence_of_element_located((AppiumBy.XPATH, "//*[@text='Add' or contains(@content-desc, 'Add')]"))).click()
        time.sleep(1)
        self.wait.until(EC.presence_of_element_located((AppiumBy.XPATH, "//android.widget.EditText[contains(@text, 'Name')]"))).send_keys("Test Customer")
        self.driver.find_element(AppiumBy.XPATH, "//android.widget.EditText[contains(@text, 'Phone')]").send_keys("9999999999")
        self.driver.find_element(AppiumBy.XPATH, "//*[@text='Save']").click()
        time.sleep(1)
        
        # Supplier
        self.driver.find_element(AppiumBy.XPATH, "//*[contains(@content-desc, 'Back') or contains(@content-desc, 'Navigate up')]").click()
        time.sleep(1)
        self.wait.until(EC.presence_of_element_located((AppiumBy.XPATH, "//*[@text='Suppliers']"))).click()
        self.wait.until(EC.presence_of_element_located((AppiumBy.XPATH, "//*[@text='Add' or contains(@content-desc, 'Add')]"))).click()
        time.sleep(1)
        self.wait.until(EC.presence_of_element_located((AppiumBy.XPATH, "//android.widget.EditText[contains(@text, 'Name')]"))).send_keys("Test Supplier")
        self.driver.find_element(AppiumBy.XPATH, "//*[@text='Save']").click()
        time.sleep(1)

    def test_05_purchase_entry(self):
        print("Testing: Purchase Entry...")
        self.login_helper()
        self.driver.find_element(AppiumBy.XPATH, "//*[@text='Purchases']").click()
        time.sleep(1)
        
        add_btn = self.wait.until(EC.presence_of_element_located((AppiumBy.XPATH, "//*[@text='Add Purchase' or contains(@content-desc, 'Add')]")))
        add_btn.click()
        time.sleep(1)
        
        # Supplier Dropdown
        self.wait.until(EC.presence_of_element_located((AppiumBy.XPATH, "//android.widget.EditText[contains(@text, 'Supplier')]"))).click()
        time.sleep(1.5)
        self.driver.find_element(AppiumBy.XPATH, "//*[@text='Test Supplier']").click()
        
        # Qty and save
        self.wait.until(EC.presence_of_element_located((AppiumBy.XPATH, "//android.widget.EditText[contains(@text, 'Qty')]"))).send_keys("100")
        self.driver.find_element(AppiumBy.XPATH, "//*[@text='Save']").click()
        time.sleep(1)

    def test_06_sales_billing_all_fields(self):
        print("Testing: Sales Billing...")
        self.login_helper()
        self.driver.find_element(AppiumBy.XPATH, "//*[@text='Sales Invoicing']").click()
        time.sleep(1)
        
        # Open Customer Dropdown
        self.wait.until(EC.presence_of_element_located((AppiumBy.XPATH, "//android.widget.EditText[contains(@text, 'Customer')]"))).click()
        time.sleep(1.5)
        self.driver.find_element(AppiumBy.XPATH, "//*[@text='Walk-in Customer']").click()
        
        # Product Dropdown
        self.driver.find_element(AppiumBy.XPATH, "//android.widget.EditText[contains(@text, 'Product')]").click()
        time.sleep(1.5)
        self.driver.find_element(AppiumBy.XPATH, "//*[@text='Sugar 1KG']").click()
        
        qty_input = self.driver.find_element(AppiumBy.XPATH, "//android.widget.EditText[contains(@text, 'Qty')]")
        qty_input.clear()
        qty_input.send_keys("2")
        
        self.driver.find_element(AppiumBy.XPATH, "//*[@text='Add to Invoice']").click()
        time.sleep(1)
        self.driver.find_element(AppiumBy.XPATH, "//*[@text='Generate & Save Bill']").click()
        time.sleep(2) # Wait for save process

    def test_07_customer_supplier_ledger(self):
        print("Testing: Ledger Views...")
        self.login_helper()
        self.driver.find_element(AppiumBy.XPATH, "//*[@text='Master Data']").click()
        time.sleep(1)
        self.wait.until(EC.presence_of_element_located((AppiumBy.XPATH, "//*[@text='Customers']"))).click()
        self.wait.until(EC.presence_of_element_located((AppiumBy.XPATH, "//*[@text='Test Customer']")))
        print("Verified Customer exists for ledger tracking")

    def test_08_reports(self):
        print("Testing: All Reports...")
        self.login_helper()
        self.driver.find_element(AppiumBy.XPATH, "//*[@text='Reports']").click()
        time.sleep(1)
        
        reports = ["Sale Amount", "Sale Bill", "Item-wise Sales", "Stock Report", "Profit & Loss", "Purchase", "Customer", "Supplier", "Expenses"]
        for report in reports:
            # Scroll might be needed if screen is small, but Appium handles visible elements
            report_elem = self.wait.until(EC.presence_of_element_located((AppiumBy.XPATH, f"//*[@text='{report}']")))
            report_elem.click()
            time.sleep(1)
            
            # Verify export button exists as proof page loaded
            self.wait.until(EC.presence_of_element_located((AppiumBy.XPATH, "//*[@text='Export PDF' or contains(@content-desc, 'Export')]")))
            self.driver.find_element(AppiumBy.XPATH, "//*[contains(@content-desc, 'Back') or contains(@content-desc, 'Navigate up')]").click()
            time.sleep(1)

    def disabled_test_09_utilities_and_backup(self):
        print("Testing: Utilities, Backup & Cashier Creation...")
        self.login_helper()
        self.driver.find_element(AppiumBy.XPATH, "//*[@text='Utilities & Settings']").click()
        time.sleep(1)
        
        # Test Backup
        self.wait.until(EC.presence_of_element_located((AppiumBy.XPATH, "//*[@text='Create Backup Archive']"))).click()
        time.sleep(2) # Wait for backup zip creation
        
        # Test Cashier Create
        self.driver.find_element(AppiumBy.XPATH, "//*[@text='Add User']").click()
        time.sleep(1)
        self.wait.until(EC.presence_of_element_located((AppiumBy.XPATH, "//android.widget.EditText[contains(@text, 'ID')]"))).send_keys("cashier1@shop.com")
        self.driver.find_element(AppiumBy.XPATH, "//android.widget.EditText[contains(@text, 'Name')]").send_keys("Cashier Raj")
        self.driver.find_element(AppiumBy.XPATH, "//android.widget.EditText[contains(@text, 'Password')]").send_keys("pass123")
        self.driver.find_element(AppiumBy.XPATH, "//*[@text='Create User']").click()
        time.sleep(2)

if __name__ == '__main__':
    unittest.main()
