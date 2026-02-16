"use client";

import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import type { Components } from "react-markdown";

const components: Components = {
  h1: ({ children }) => (
    <h1 className="text-sm font-bold mt-3 mb-1.5 first:mt-0">{children}</h1>
  ),
  h2: ({ children }) => (
    <h2 className="text-[13px] font-bold mt-2.5 mb-1 first:mt-0">{children}</h2>
  ),
  h3: ({ children }) => (
    <h3 className="text-[12px] font-semibold mt-2 mb-1 first:mt-0">{children}</h3>
  ),
  p: ({ children }) => <p className="mb-1.5 last:mb-0">{children}</p>,
  ul: ({ children }) => <ul className="ml-3.5 mb-1.5 last:mb-0 list-disc space-y-0.5">{children}</ul>,
  ol: ({ children }) => <ol className="ml-3.5 mb-1.5 last:mb-0 list-decimal space-y-0.5">{children}</ol>,
  li: ({ children }) => <li className="text-[12.5px] leading-relaxed">{children}</li>,
  strong: ({ children }) => <strong className="font-semibold">{children}</strong>,
  em: ({ children }) => <em className="italic">{children}</em>,
  code: ({ children, className }) => {
    const isBlock = className?.includes("language-");
    if (isBlock) {
      return (
        <code className="block bg-black/10 dark:bg-white/10 rounded px-2 py-1 text-[11px] font-mono overflow-x-auto my-1">
          {children}
        </code>
      );
    }
    return (
      <code className="bg-black/8 dark:bg-white/12 rounded px-1 py-0.5 text-[11.5px] font-mono">
        {children}
      </code>
    );
  },
  pre: ({ children }) => <pre className="my-1">{children}</pre>,
  hr: () => <hr className="my-2 border-current opacity-15" />,
  blockquote: ({ children }) => (
    <blockquote className="border-l-2 border-current/20 pl-2.5 my-1.5 opacity-80 italic">
      {children}
    </blockquote>
  ),
  table: ({ children }) => (
    <div className="overflow-x-auto my-1.5">
      <table className="text-[11px] border-collapse w-full">{children}</table>
    </div>
  ),
  th: ({ children }) => (
    <th className="border border-current/15 px-2 py-1 text-left font-semibold bg-black/5 dark:bg-white/5">
      {children}
    </th>
  ),
  td: ({ children }) => (
    <td className="border border-current/10 px-2 py-0.5">{children}</td>
  ),
};

interface Props {
  content: string;
  className?: string;
}

export function MarkdownMessage({ content, className }: Props) {
  return (
    <div className={className}>
      <ReactMarkdown remarkPlugins={[remarkGfm]} components={components}>
        {content}
      </ReactMarkdown>
    </div>
  );
}
