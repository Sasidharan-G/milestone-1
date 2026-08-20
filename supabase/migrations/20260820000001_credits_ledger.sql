-- Customer Credits Table
CREATE TABLE IF NOT EXISTS public.customer_credits (
    id TEXT PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES public.companies(id) ON DELETE CASCADE,
    customer_id TEXT NOT NULL REFERENCES public.customers(id) ON DELETE CASCADE,
    amount_minor_units BIGINT NOT NULL,
    reason TEXT NOT NULL,
    date_epoch_ms BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE public.customer_credits ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Enable read access for company users" ON public.customer_credits
FOR SELECT USING (public.is_company_member(company_id));

CREATE POLICY "Enable insert for company users" ON public.customer_credits
FOR INSERT WITH CHECK (public.is_company_member(company_id));

CREATE POLICY "Enable update for company users" ON public.customer_credits
FOR UPDATE USING (public.is_company_member(company_id));

CREATE POLICY "Enable delete for company users" ON public.customer_credits
FOR DELETE USING (public.is_company_member(company_id));


-- Supplier Credits Table
CREATE TABLE IF NOT EXISTS public.supplier_credits (
    id TEXT PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES public.companies(id) ON DELETE CASCADE,
    supplier_id TEXT NOT NULL REFERENCES public.suppliers(id) ON DELETE CASCADE,
    amount_minor_units BIGINT NOT NULL,
    terms TEXT NOT NULL,
    due_date_epoch_ms BIGINT NOT NULL,
    date_epoch_ms BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE public.supplier_credits ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Enable read access for company users" ON public.supplier_credits
FOR SELECT USING (public.is_company_member(company_id));

CREATE POLICY "Enable insert for company users" ON public.supplier_credits
FOR INSERT WITH CHECK (public.is_company_member(company_id));

CREATE POLICY "Enable update for company users" ON public.supplier_credits
FOR UPDATE USING (public.is_company_member(company_id));

CREATE POLICY "Enable delete for company users" ON public.supplier_credits
FOR DELETE USING (public.is_company_member(company_id));
