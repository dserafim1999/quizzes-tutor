describe('Teacher walkthrough', () => {
  beforeEach(() => {
    cy.demoTeacherLogin();
    cy.log('student submits a question');
    cy.addQuestionSubmission('Test', 'IN_REVISION', 676);
    cy.get('[data-cy="managementMenuButton"]').click();
    cy.get('[data-cy="submissionTeacherMenuButton"]').click();
  });

  afterEach(() => {
    cy.deleteQuestionSubmission('Test', 6);
    cy.contains('Logout').click();
  });

  it('login approves a submission (AVAILABLE)', () => {
    cy.reviewQuestionSubmission('Approve (AVAILABLE)', 'Test', 'Comment');
    cy.checkQuestionSubmissionStatus('Test', 'APPROVED');
  });

  it('login approves a submission (DISABLED)', () => {
    cy.reviewQuestionSubmission('Approve (DISABLED)', 'Test', 'Comment');
    cy.checkQuestionSubmissionStatus('Test', 'APPROVED');
  });

  it('login rejects a submission', () => {
    cy.reviewQuestionSubmission('Reject', 'Test', 'Comment');
    cy.checkQuestionSubmissionStatus('Test', 'REJECTED');
  });

  it('login requests changes for a submission', () => {
      cy.reviewQuestionSubmission('Request Changes', 'Test', 'Comment');
      cy.checkQuestionSubmissionStatus('Test', 'QUESTION STATUS');
  });

  it('login requests further review', () => {
      cy.reviewQuestionSubmission('Request Further Review', 'Test', 'Comment');
      cy.checkQuestionSubmissionStatus('Test', 'FURTHER REVIEW REQUESTED');
  });

  it('login reviews submisssion without comment', () => {
    cy.reviewQuestionSubmission('Approve (AVAILABLE)', 'Test');

    cy.closeErrorMessage('Review must have comment');

    cy.log('close dialog');
    cy.get('[data-cy="CloseButton"]').click();
  });
});