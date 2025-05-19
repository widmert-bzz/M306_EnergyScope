import { Injectable } from '@angular/core';
import { Subject } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class SidebarService {
  private toggleSidebarSource = new Subject<boolean>();

  // Observable that components can subscribe to
  toggleSidebar$ = this.toggleSidebarSource.asObservable();

  constructor() {
    // Subscribe to our own subject to track state changes
    this.toggleSidebar$.subscribe(state => {
      this.currentState = state;
    });
  }

  // Method to toggle the sidebar
  toggleSidebar(open?: boolean) {
    // Ensure we always pass a boolean value to the Subject
    this.toggleSidebarSource.next(open === undefined ? !this.currentState : open);
  }

  // Track the current state
  private currentState = false;
}
