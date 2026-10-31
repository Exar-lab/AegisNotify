import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { SidebarComponent } from './sidebar.component';

describe('SidebarComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SidebarComponent],
      providers: [provideRouter([])],
    }).compileComponents();
  });

  it('should create the sidebar component', () => {
    const fixture = TestBed.createComponent(SidebarComponent);
    const component = fixture.componentInstance;
    expect(component).toBeTruthy();
  });

  it('should include Users item in navItems', () => {
    const fixture = TestBed.createComponent(SidebarComponent);
    const component = fixture.componentInstance;
    const usersItem = component.navItems.find((item) => item.route === '/users');
    expect(usersItem).toBeDefined();
    expect(usersItem).toEqual({
      label: 'Users',
      route: '/users',
      icon: 'users',
    });
  });

  it('should render the Users link and icon in DOM', () => {
    const fixture = TestBed.createComponent(SidebarComponent);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;

    const usersLink = element.querySelector('a[href="/users"]');
    expect(usersLink).not.toBeNull();
    expect(usersLink?.textContent).toContain('Users');

    const svgIcon = usersLink?.querySelector('svg');
    expect(svgIcon).not.toBeNull();
  });
});
