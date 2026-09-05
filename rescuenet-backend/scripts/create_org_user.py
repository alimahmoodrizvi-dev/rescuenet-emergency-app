"""
Provisions a Volunteer / Rescue Operator / Command Center Admin account (Part 24). There is
deliberately no public API endpoint for this — see the docstring on
app/routers/auth.py::org_login — so this script is the intended way to create the first
Command Center login for local development or a demo.

Usage:
    python scripts/create_org_user.py --username admin --password changeme --role COMMAND_CENTER_ADMIN

Run from the rescuenet-backend/ directory so the `app` package resolves correctly.
"""
import argparse
import sys

sys.path.insert(0, ".")

from app.auth import hash_password  # noqa: E402
from app.database import Base, SessionLocal, engine  # noqa: E402
from app.models import User, UserRole  # noqa: E402


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--username", required=True)
    parser.add_argument("--password", required=True)
    parser.add_argument("--role", required=True, choices=[r.value for r in UserRole])
    args = parser.parse_args()

    Base.metadata.create_all(bind=engine)
    db = SessionLocal()
    try:
        existing = db.query(User).filter(User.name == args.username).first()
        if existing:
            print(f"User '{args.username}' already exists (id={existing.id}). Updating password/role.")
            existing.hashed_password = hash_password(args.password)
            existing.role = UserRole(args.role)
        else:
            user = User(name=args.username, role=UserRole(args.role), hashed_password=hash_password(args.password))
            db.add(user)
            print(f"Created user '{args.username}' with role {args.role}.")
        db.commit()
    finally:
        db.close()


if __name__ == "__main__":
    main()
