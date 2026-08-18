# System architecture

Android is the operational source while offline: Compose UI → ViewModel → use case → repository → Room. Synchronizable writes carry UUID, timestamps, and sync status. The Node/Express/MongoDB backend is only for authenticated online capability and sync. Business rules that lack approval remain strategy interfaces, never guessed calculations.

