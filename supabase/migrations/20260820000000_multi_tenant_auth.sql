-- 1. Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 2. Create Companies Table
CREATE TABLE IF NOT EXISTS public.companies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    owner_user_id UUID NOT NULL,
    status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'suspended')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 3. Create User Profiles Table
CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    full_name TEXT NOT NULL,
    phone TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 4. Create Company Users Membership Table
CREATE TABLE IF NOT EXISTS public.company_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES public.companies(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    role TEXT NOT NULL CHECK (role IN ('SUPER_ADMIN', 'COMPANY_ADMIN', 'CASHIER')),
    permissions TEXT[] NOT NULL DEFAULT '{}',
    status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'suspended')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(company_id, user_id)
);

-- 5. Helper Function for RLS to check Company Membership
CREATE OR REPLACE FUNCTION public.is_company_member(company_id uuid)
RETURNS boolean AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 
        FROM public.company_users 
        WHERE company_users.company_id = $1 
          AND company_users.user_id = auth.uid() 
          AND company_users.status = 'active'
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 6. Helper Function for RLS to check User Role
CREATE OR REPLACE FUNCTION public.get_user_role(p_company_id uuid)
RETURNS text AS $$
DECLARE
    v_user_role text;
BEGIN
    SELECT role INTO v_user_role 
    FROM public.company_users 
    WHERE company_id = p_company_id 
      AND user_id = auth.uid() 
      AND status = 'active'
    LIMIT 1;
    RETURN v_user_role;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 7. Add company_id to tenant-owned business tables
ALTER TABLE public.categories ADD COLUMN IF NOT EXISTS company_id UUID REFERENCES public.companies(id) ON DELETE CASCADE;
ALTER TABLE public.products ADD COLUMN IF NOT EXISTS company_id UUID REFERENCES public.companies(id) ON DELETE CASCADE;
ALTER TABLE public.customers ADD COLUMN IF NOT EXISTS company_id UUID REFERENCES public.companies(id) ON DELETE CASCADE;
ALTER TABLE public.suppliers ADD COLUMN IF NOT EXISTS company_id UUID REFERENCES public.companies(id) ON DELETE CASCADE;
ALTER TABLE public.expenses ADD COLUMN IF NOT EXISTS company_id UUID REFERENCES public.companies(id) ON DELETE CASCADE;
ALTER TABLE public.sales ADD COLUMN IF NOT EXISTS company_id UUID REFERENCES public.companies(id) ON DELETE CASCADE;
ALTER TABLE public.sale_items ADD COLUMN IF NOT EXISTS company_id UUID REFERENCES public.companies(id) ON DELETE CASCADE;
ALTER TABLE public.purchases ADD COLUMN IF NOT EXISTS company_id UUID REFERENCES public.companies(id) ON DELETE CASCADE;
ALTER TABLE public.purchase_items ADD COLUMN IF NOT EXISTS company_id UUID REFERENCES public.companies(id) ON DELETE CASCADE;

-- 8. Enable RLS on all tables
ALTER TABLE public.companies ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.company_users ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.products ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.customers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.suppliers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.expenses ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sales ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sale_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.purchases ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.purchase_items ENABLE ROW LEVEL SECURITY;

-- 9. Row Level Security (RLS) Policies
-- companies
CREATE POLICY select_companies ON public.companies FOR SELECT USING (
    public.is_company_member(id)
);
CREATE POLICY insert_companies ON public.companies FOR INSERT WITH CHECK (
    owner_user_id = auth.uid()
);
CREATE POLICY update_companies ON public.companies FOR UPDATE USING (
    owner_user_id = auth.uid() AND public.get_user_role(id) = 'COMPANY_ADMIN'
);

-- profiles
CREATE POLICY select_profiles ON public.profiles FOR SELECT USING (
    id = auth.uid() OR id IN (
        SELECT user_id FROM public.company_users WHERE public.is_company_member(company_id)
    )
);
CREATE POLICY insert_profiles ON public.profiles FOR INSERT WITH CHECK (id = auth.uid());
CREATE POLICY update_profiles ON public.profiles FOR UPDATE USING (id = auth.uid());

-- company_users
CREATE POLICY select_company_users ON public.company_users FOR SELECT USING (
    public.is_company_member(company_id)
);
CREATE POLICY manage_company_users ON public.company_users FOR ALL USING (
    public.get_user_role(company_id) = 'COMPANY_ADMIN'
);

-- Categories & Products (Read-Only for Casher, Full for Admin)
CREATE POLICY select_categories ON public.categories FOR SELECT USING (public.is_company_member(company_id));
CREATE POLICY manage_categories ON public.categories FOR ALL USING (
    public.is_company_member(company_id) AND public.get_user_role(company_id) = 'COMPANY_ADMIN'
);

CREATE POLICY select_products ON public.products FOR SELECT USING (public.is_company_member(company_id));
CREATE POLICY manage_products ON public.products FOR ALL USING (
    public.is_company_member(company_id) AND public.get_user_role(company_id) = 'COMPANY_ADMIN'
);

-- Business Tables (Access granted if active member of the company)
CREATE POLICY access_customers ON public.customers FOR ALL USING (public.is_company_member(company_id));
CREATE POLICY access_suppliers ON public.suppliers FOR ALL USING (public.is_company_member(company_id));
CREATE POLICY access_expenses ON public.expenses FOR ALL USING (public.is_company_member(company_id));
CREATE POLICY access_sales ON public.sales FOR ALL USING (public.is_company_member(company_id));
CREATE POLICY access_sale_items ON public.sale_items FOR ALL USING (public.is_company_member(company_id));
CREATE POLICY access_purchases ON public.purchases FOR ALL USING (public.is_company_member(company_id));
CREATE POLICY access_purchase_items ON public.purchase_items FOR ALL USING (public.is_company_member(company_id));

-- 10. Atomic Company Registration RPC Function
CREATE OR REPLACE FUNCTION public.initialize_new_company(business_name text, owner_name text)
RETURNS uuid AS $$
DECLARE
    new_company_id uuid;
BEGIN
    -- Verify caller is authenticated
    IF auth.uid() IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    -- Verify caller doesn't already belong to a company
    IF EXISTS (SELECT 1 FROM public.company_users WHERE user_id = auth.uid() AND status = 'active') THEN
        RAISE EXCEPTION 'User already associated with an active company';
    END IF;

    -- Create company
    INSERT INTO public.companies (name, owner_user_id, status)
    VALUES (business_name, auth.uid(), 'active')
    RETURNING id INTO new_company_id;

    -- Create profile if missing
    INSERT INTO public.profiles (id, full_name)
    VALUES (auth.uid(), owner_name)
    ON CONFLICT (id) DO UPDATE SET full_name = owner_name;

    -- Add membership as owner
    INSERT INTO public.company_users (company_id, user_id, role, status, permissions)
    VALUES (
        new_company_id,
        auth.uid(),
        'COMPANY_ADMIN',
        'active',
        ARRAY['USER_MANAGE','CATEGORY_VIEW','CATEGORY_CREATE','CATEGORY_EDIT','PRODUCT_VIEW','PRODUCT_CREATE','PRODUCT_EDIT','SALE_CREATE','SALE_VIEW','PURCHASE_CREATE','PURCHASE_VIEW','REPORT_SALES','REPORT_STOCK','REPORT_PROFIT','BACKUP_CREATE','SETTINGS_VIEW','SETTINGS_EDIT']
    );

    RETURN new_company_id;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 11. Atomic Cashier Provisioning RPC Function (Bypasses service_role on client)
CREATE OR REPLACE FUNCTION public.create_cashier_user(
    cashier_email text,
    cashier_password text,
    cashier_display_name text,
    cashier_permissions text[]
)
RETURNS uuid AS $$
DECLARE
    caller_company_id uuid;
    new_user_id uuid;
BEGIN
    -- Verify caller is COMPANY_ADMIN
    SELECT company_id INTO caller_company_id
    FROM public.company_users
    WHERE user_id = auth.uid() AND role = 'COMPANY_ADMIN' AND status = 'active';

    IF caller_company_id IS NULL THEN
        RAISE EXCEPTION 'Access Denied: Only active company administrators can create cashiers';
    END IF;

    -- Check if auth user already exists in auth.users
    SELECT id INTO new_user_id FROM auth.users WHERE email = cashier_email;

    IF new_user_id IS NULL THEN
        -- Generate User UUID
        new_user_id := gen_random_uuid();
        -- Insert auth user (hashing password via crypt/pgcrypto)
        INSERT INTO auth.users (id, email, encrypted_password, email_confirmed_at, raw_app_meta_data, raw_user_meta_data, created_at, updated_at, role, aud)
        VALUES (
            new_user_id,
            cashier_email,
            extensions.crypt(cashier_password, extensions.gen_salt('bf')),
            now(),
            '{"provider":"email","providers":["email"]}',
            jsonb_build_object('display_name', cashier_display_name),
            now(),
            now(),
            'authenticated',
            'authenticated'
        );
    END IF;

    -- Add to profile
    INSERT INTO public.profiles (id, full_name)
    VALUES (new_user_id, cashier_display_name)
    ON CONFLICT (id) DO NOTHING;

    -- Add membership
    INSERT INTO public.company_users (company_id, user_id, role, status, permissions)
    VALUES (caller_company_id, new_user_id, 'CASHIER', 'active', cashier_permissions)
    ON CONFLICT (company_id, user_id) DO UPDATE 
    SET role = 'CASHIER', status = 'active', permissions = cashier_permissions;

    RETURN new_user_id;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
